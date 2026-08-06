package com.teenyfin.teenymoney.global.exception;

import com.teenyfin.teenymoney.global.response.ApiResponse;
import com.teenyfin.teenymoney.global.storage.StorageErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 모든 예외를 ApiResponse 형태의 JSON으로 바꾼다.
 *
 * 원칙: 예외 메시지를 그대로 응답에 넣지 않는다.
 * SQL 문장이나 내부 경로가 노출된다. 사용자에게는 ErrorCode의 정해진 문구만 보내고
 * 실제 원인은 로그에만 남긴다.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionAdvice {

    /** 업무 규칙 위반. 예상된 실패라 스택트레이스까지 남기지 않는다. */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        log.warn("BusinessException: code={}, message={}", code.getCode(), code.getMessage());
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code));
    }

    /** @Valid 검증 실패. 어느 필드가 왜 틀렸는지 data에 담아준다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        log.warn("검증 실패: {}", fieldErrors);
        return ResponseEntity.status(CommonErrorCode.COMMON_INVALID_INPUT.getStatus())
                .body(ApiResponse.error(CommonErrorCode.COMMON_INVALID_INPUT,
                        CommonErrorCode.COMMON_INVALID_INPUT.getMessage(), fieldErrors));
    }

    /** 본문 JSON을 파싱하지 못함 (형식 오류, 타입 불일치 등) */
    /**
     * @RequestHeader(required = true) 인데 헤더가 없을 때.
     *
     * 이 핸들러가 없으면 아래 catch-all에 걸려 500이 나간다. 클라이언트 잘못인데
     * 서버 장애처럼 보여서, 프론트가 무엇을 빠뜨렸는지 알 수 없다.
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException e) {
        log.warn("필수 헤더 누락: {}", e.getHeaderName());
        return ResponseEntity
                .status(CommonErrorCode.COMMON_MISSING_HEADER.getStatus())
                .body(ApiResponse.error(
                        CommonErrorCode.COMMON_MISSING_HEADER,
                        e.getHeaderName() + " 헤더가 필요합니다.",
                        null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException e) {
        log.warn("본문 파싱 실패: {}", e.getMessage());
        return status(CommonErrorCode.COMMON_MALFORMED_JSON);
    }

    /** 파라미터 타입 불일치 (예: /wallets/abc 처럼 숫자 자리에 문자) */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("파라미터 타입 불일치: {}", e.getName());
        return status(CommonErrorCode.COMMON_INVALID_TYPE);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        log.warn("지원하지 않는 메서드: {}", e.getMethod());
        return status(CommonErrorCode.COMMON_METHOD_NOT_ALLOWED);
    }

    /** 없는 URL. WebConfig의 throwExceptionIfNoHandlerFound=true 덕분에 여기로 온다. */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoHandlerFoundException e) {
        log.warn("경로 없음: {} {}", e.getHttpMethod(), e.getRequestURL());
        return status(CommonErrorCode.COMMON_NOT_FOUND);
    }

    /** 권한 없음 (자녀가 부모 전용 API를 호출하는 등) */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        log.warn("권한 없음: {}", e.getMessage());
        return status(CommonErrorCode.AUTH_FORBIDDEN);
    }

    /**
     * 컨테이너 레벨 용량 상한 초과.
     *
     * WebConfig의 MultipartConfigElement가 애플리케이션 코드에 닿기 전에 요청을 끊으므로
     * ImageFile의 검사와 별개로 여기서 받아야 한다. 처리하지 않으면 아래 Exception
     * 핸들러에 걸려 500 COMMON_INTERNAL_ERROR가 나가고, 사용자 잘못이 서버 오류로 보인다.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("업로드 용량 초과: {}", e.getMessage());
        return status(StorageErrorCode.STORAGE_FILE_TOO_LARGE);
    }

    /** 예상 못 한 오류. 스택트레이스는 로그에만 남기고 응답에는 정해진 문구만 보낸다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return status(CommonErrorCode.COMMON_INTERNAL_ERROR);
    }

    private ResponseEntity<ApiResponse<Void>> status(ErrorCode code) {
        return ResponseEntity.status(code.getStatus()).body(ApiResponse.error(code));
    }
}
