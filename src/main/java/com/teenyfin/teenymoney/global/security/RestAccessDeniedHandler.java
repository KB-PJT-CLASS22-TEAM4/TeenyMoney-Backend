package com.teenyfin.teenymoney.global.security;

import com.teenyfin.teenymoney.global.exception.CommonErrorCode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 인가 실패(403) — "누군지는 알지만 권한이 없다".
 * 예: CHILD 역할이 PARENT 전용 API를 호출한 경우.
 *
 * 인증 실패(401)와 달리 분기가 없다. 인가 실패는 이미 인증이 끝난 뒤의 문제이므로
 * 토큰 관련 사유(만료/위조)를 볼 필요가 없고, 사유도 '권한 부족' 하나뿐이다.
 *
 * 등록은 SecurityConfig의 exceptionHandling에서 한다(Task 4).
 * 실제로 호출되려면 @PreAuthorize 같은 role 게이팅이 필요하다(향후 이슈).
 */
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        // "PARENT 권한이 필요합니다" 같은 구체적 사유를 주지 않는다.
        // 어떤 역할이 어떤 API에 접근 가능한지 알려주면 공격 대상을 좁히는 힌트가 된다.
        // 정당한 사용자에게는 앱 화면에서 이미 안내되어야 할 정보다.
        ErrorResponseWriter.write(response, CommonErrorCode.AUTH_FORBIDDEN);
    }
}
