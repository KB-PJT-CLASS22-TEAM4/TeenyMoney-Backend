package com.teenyfin.teenymoney.global.security.jwt;

import com.teenyfin.teenymoney.domain.auth.exception.AuthErrorCode;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * Authorization: Bearer <access> 를 검증해 SecurityContext에 인증을 채운다.
 *
 * 이 필터가 없으면 엔드포인트마다 헤더를 꺼내고 parse를 부르는 코드를 반복해야 하고,
 * 한 곳만 빼먹어도 그 API가 인증 없이 뚫린다. 필터는 모든 요청이 지나는 통로라
 * 여기 한 번만 두면 전체에 적용된다.
 *
 * 판단은 4갈래이고 네 경우 모두 요청을 다음 단계로 넘긴다.
 *   1) 헤더 없음        : 인증 안 함, 사유 없음        → 익명 통과
 *   2) 유효 + ACCESS    : 인증 채움                    → 통과
 *   3) 만료             : 인증 안 함, EXPIRED 표기     → 통과
 *   4) 위조/ACCESS 아님 : 인증 안 함, INVALID 표기     → 통과
 *
 * 실패해도 끊지 않는 이유: 이 필터의 책임은 '누구인지 확인'이고 '차단'이 아니다.
 * 어느 경로가 인증을 요구하는지는 SecurityConfig의 인가 규칙이 아는 정보다.
 * 또 앱이 저장된 토큰을 모든 요청에 자동으로 붙이는 경우가 흔해서, 만료된 토큰이
 * 로그인 요청에 실려 오는 것이 정상 시나리오다. 여기서 401을 내면 로그인조차 못 한다.
 *
 * 토큰 값과 비밀키는 로그에 남기지 않는다. 그래서 이 클래스에는 로거가 아예 없다.
 * catch에서 토큰을 찍으면 유효한 Access Token이 로그에 평문으로 쌓인다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * 인증 실패 사유를 남기는 request attribute 키.
     * RestAuthenticationEntryPoint가 같은 키로 읽는다. 양쪽이 문자열을 각자 쓰면
     * 오타 하나로 사유가 항상 유실되고 기본 메시지만 나가므로 상수로 공유한다.
     */
    public static final String AUTH_ERROR_ATTRIBUTE = "authError";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    public JwtAuthenticationFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    // OncePerRequestFilter를 상속해 doFilterInternal을 구현하면 요청당 정확히 한 번만 실행된다.
    // Filter를 직접 구현하면 forward/include/비동기에서 필터가 다시 타면서
    // 토큰을 두 번 파싱하고 SecurityContext를 두 번 덮어쓴다.
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // [분기 1] 헤더가 없거나 Bearer 형식이 아니면 인증을 시도하지 않고 그냥 통과시킨다.
        // 로그인·회원가입·헬스체크는 토큰 없이 호출돼야 하는 엔드포인트다.
        // 사유(authError)도 남기지 않는다. '토큰을 안 보낸 것'은 오류가 아니다.
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());
        try {
            // 서명·만료 검증. 실패하면 아래 catch로 간다.
            Claims claims = jwtProvider.parse(token);

            // [분기 4-a] Refresh 토큰을 Authorization 헤더로 보낸 경우.
            // Refresh는 Access와 같은 키로 서명되므로 위 parse를 정상 통과한다(만료도 안 됨).
            // 즉 이 비교가 둘을 막는 유일한 근거다. 빼면 14일짜리 Refresh로 모든 API를
            // 호출할 수 있어 Access를 30분으로 줄인 의미가 사라진다.
            if (!JwtProvider.TOKEN_TYPE_ACCESS.equals(claims.get(JwtProvider.CLAIM_TOKEN_TYPE, String.class))) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, AuthErrorCode.AUTH_TOKEN_INVALID);
            } else {
                // [분기 2] 정상 Access 토큰 → SecurityContext에 인증을 채운다.

                // sub는 JWT 표준상 문자열이므로 Long으로 되돌린다.
                Long memberId = Long.valueOf(claims.getSubject());
                String role = claims.get(JwtProvider.CLAIM_ROLE, String.class);

                // DB 조회 없이 토큰 클레임만으로 인증 주체를 만든다.
                // 컨트롤러가 @AuthenticationPrincipal MemberPrincipal 로 받는다.
                MemberPrincipal principal = new MemberPrincipal(memberId, role);

                // 3-인자 생성자는 authenticated=true로 세팅된다.
                // 2-인자 생성자를 쓰면 '아직 검증 안 된 인증 시도'로 취급되어 인가가 실패한다.
                // credentials(2번째 인자)는 null — JWT 인증이라 비밀번호가 없고, 남길 이유도 없다.
                // 권한에 ROLE_ 접두사를 붙이는 이유: hasRole('PARENT')는 내부적으로 ROLE_PARENT를
                // 찾는다. 접두사 없이 담으면 hasRole이 항상 거짓이 되어 부모도 403을 받는다.
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                principal, null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (ExpiredJwtException e) {
            // [분기 3] 만료. INVALID와 구별해야 FE가 '재발급 시도'와 '재로그인'을 나눌 수 있다.
            // ExpiredJwtException은 JwtException의 하위 타입이므로 반드시 먼저 잡는다.
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, AuthErrorCode.AUTH_TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            // [분기 4-b] 서명 불일치·형식 손상 등. IllegalArgumentException은
            // Long.valueOf(sub)가 숫자가 아닌 값을 만났을 때도 발생한다.
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, AuthErrorCode.AUTH_TOKEN_INVALID);
        }

        // 인증 성공/실패와 무관하게 항상 다음 단계로 넘긴다.
        // 401을 낼지는 인가 규칙과 RestAuthenticationEntryPoint가 판단한다.
        filterChain.doFilter(request, response);
    }
}
