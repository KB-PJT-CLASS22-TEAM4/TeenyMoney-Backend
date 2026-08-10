package com.teenyfin.teenymoney.global.security;

import com.teenyfin.teenymoney.global.exception.CommonErrorCode;
import com.teenyfin.teenymoney.global.exception.ErrorCode;
import com.teenyfin.teenymoney.global.security.jwt.JwtAuthenticationFilter;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 인증 실패(401) — "네가 누군지 모르겠다".
 *
 * 권한 부족(403)과 반드시 구별해야 한다. FE는 401에서 재로그인을 유도하고
 * 403에서는 "권한이 없습니다"를 띄운다. 둘을 섞으면 이미 로그인한 사용자가
 * 권한 없는 화면을 누를 때마다 로그인 화면으로 무한히 튕긴다.
 *
 * Spring Security가 인증되지 않은 요청을 거부할 때 이 클래스를 호출한다.
 * 등록은 SecurityConfig의 exceptionHandling에서 한다.
 */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // 파라미터로 받은 authException은 쓰지 않는다.
        // 필터가 예외를 던지지 않고 통과시키므로(JwtAuthenticationFilter 참고), 여기 오는 예외는
        // Spring Security가 만든 일반 예외이고 JWT 관련 정보가 없다.
        // 실패 사유는 필터가 남긴 request attribute가 유일한 경로다.
        Object attribute = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);

        // instanceof가 null도 함께 걸러준다. (ErrorCode) null 로 캐스팅하면
        // 아래 getStatus()에서 NPE가 터져 401이 아니라 500이 나간다.
        //   메모 있음 → 그 사유로 (AUTH_TOKEN_EXPIRED / AUTH_TOKEN_INVALID)
        //   메모 없음 → 토큰 자체를 안 보낸 경우이므로 기본값
        ErrorCode errorCode = (attribute instanceof ErrorCode)
                ? (ErrorCode) attribute
                : CommonErrorCode.AUTH_UNAUTHORIZED;
        ErrorResponseWriter.write(response, errorCode);
    }
}
