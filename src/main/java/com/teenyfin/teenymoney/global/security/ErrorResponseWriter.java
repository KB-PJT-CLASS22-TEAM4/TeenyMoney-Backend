package com.teenyfin.teenymoney.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teenyfin.teenymoney.global.exception.ErrorCode;
import com.teenyfin.teenymoney.global.response.ApiResponse;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 시큐리티 필터 단계(DispatcherServlet 이전)에서 던져지는 인증/인가 예외는
 * @RestControllerAdvice가 잡지 못한다. 그래서 진입점/핸들러가 직접 JSON을 쓴다.
 *
 *   필터 체인 ├─ JwtAuthenticationFilter
 *             └─ 인가 판단 → 실패!        ← 여기서 응답이 끝난다
 *   DispatcherServlet
 *             └─ @RestControllerAdvice    ← 여기까지 내려오지 않는다
 *
 * RestAuthenticationEntryPoint(401)와 RestAccessDeniedHandler(403)가 같은 코드를
 * 쓰므로 한 곳에 모았다. package-private이라 global.security 밖에서는 보이지 않는다 —
 * 컨트롤러는 ApiResponse를 반환하거나 예외를 던져 @RestControllerAdvice에 맡겨야 한다.
 */
final class ErrorResponseWriter {

    // ObjectMapper 생성은 무겁고(내부 직렬화 캐시) 설정을 바꾸지 않으면 스레드 안전하다.
    // 인증 실패마다 새로 만들 이유가 없어 하나를 공유한다.
    // static으로 두면 핸들러를 스프링 없이 new로 만들 수 있어 테스트가 단순해진다.
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ErrorResponseWriter() {
    }

    static void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        // 상태 코드를 숫자로 쓰지 않고 ErrorCode에서 꺼낸다.
        // AUTH_UNAUTHORIZED=401, AUTH_FORBIDDEN=403 이라는 사실이 enum 한 곳에만 있게 한다.
        response.setStatus(errorCode.getStatus().value());
        // charset을 명시하지 않으면 "로그인이 필요합니다" 같은 한글 메시지가 깨진다.
        response.setContentType("application/json;charset=UTF-8");
        // 성공 응답과 같은 ApiResponse 형식으로 감싼다. FE는 success 필드로만 분기한다.
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(ApiResponse.error(errorCode)));
    }
}
