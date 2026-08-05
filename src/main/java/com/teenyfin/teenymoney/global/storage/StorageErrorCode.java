package com.teenyfin.teenymoney.global.storage;

import com.teenyfin.teenymoney.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 파일 스토리지 에러 코드.
 *
 * 업로드 실패만 503이다. 원인이 S3 장애나 자격증명 문제라 사용자가 요청을 고쳐서
 * 해결할 수 없기 때문이다. 400으로 주면 클라이언트가 "내 파일이 잘못됐구나"로
 * 오해하고 재시도를 포기한다. 나머지 셋은 파일을 바꾸면 해결되므로 400이다.
 */
@Getter
@RequiredArgsConstructor
public enum StorageErrorCode implements ErrorCode {

    STORAGE_FILE_EMPTY(HttpStatus.BAD_REQUEST, "업로드할 파일이 없습니다."),
    STORAGE_FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "이미지 크기는 5MB를 넘을 수 없습니다."),
    STORAGE_UNSUPPORTED_TYPE(HttpStatus.BAD_REQUEST, "jpg, png, webp 이미지만 업로드할 수 있습니다."),
    STORAGE_UPLOAD_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "이미지 업로드에 실패했습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
