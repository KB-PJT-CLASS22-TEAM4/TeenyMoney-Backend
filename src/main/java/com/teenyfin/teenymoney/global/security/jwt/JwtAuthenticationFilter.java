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
 * - 헤더 없음: 익명 통과(인가 규칙이 401 여부 판단)
 * - 유효 + tokenType=ACCESS: 인증
 * - 그 외(만료/손상/ACCESS 아님): 인증하지 않고 request attribute에 사유 표기 → 진입점이 401 처리
 * 토큰 값과 비밀키는 로그에 남기지 않는다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_ERROR_ATTRIBUTE = "authError";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    public JwtAuthenticationFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());
        try {
            Claims claims = jwtProvider.parse(token);
            if (!JwtProvider.TOKEN_TYPE_ACCESS.equals(claims.get(JwtProvider.CLAIM_TOKEN_TYPE, String.class))) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, AuthErrorCode.AUTH_TOKEN_INVALID);
            } else {
                Long memberId = Long.valueOf(claims.getSubject());
                String role = claims.get(JwtProvider.CLAIM_ROLE, String.class);
                MemberPrincipal principal = new MemberPrincipal(memberId, role);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                principal, null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (ExpiredJwtException e) {
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, AuthErrorCode.AUTH_TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, AuthErrorCode.AUTH_TOKEN_INVALID);
        }

        filterChain.doFilter(request, response);
    }
}
