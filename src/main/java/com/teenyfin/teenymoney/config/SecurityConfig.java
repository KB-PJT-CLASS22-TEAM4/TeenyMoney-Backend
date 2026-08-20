package com.teenyfin.teenymoney.config;

import com.teenyfin.teenymoney.global.auth.RefreshTokenStore;
import com.teenyfin.teenymoney.global.security.RestAccessDeniedHandler;
import com.teenyfin.teenymoney.global.security.RestAuthenticationEntryPoint;
import com.teenyfin.teenymoney.global.security.jwt.JwtAuthenticationFilter;
import com.teenyfin.teenymoney.global.security.jwt.JwtProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.Arrays;

/**
 * Spring Security 설정 (루트 컨텍스트).
 *
 * 인증 파이프라인의 조각들이 여기서 하나로 조립된다. 이 파일에 등록되지 않은 조각은
 * 클래스만 존재할 뿐 실제 요청에 아무 영향이 없다.
 *   JwtProvider              토큰 발급·검증
 *   JwtAuthenticationFilter  매 요청 인증
 *   Rest*EntryPoint/Handler  401/403 JSON 응답
 *
 * 보안 빈은 여기 @Bean으로 등록한다. 필터체인은 루트 컨텍스트에 있어야 하고
 * (DelegatingFilterProxy가 루트에서만 springSecurityFilterChain을 찾는다),
 * @Bean으로 두면 스캔 설정이 어떻게 바뀌든 이 빈들이 루트에 있는 것이 보장된다.
 *
 * 인가 규칙은 '공개 경로 화이트리스트 + 나머지 전부 인증'이다. 화이트리스트에는
 * 토큰을 아직 못 받았거나 받을 수 없는 상태에서 호출해야 하는 경로만 넣는다.
 * 여기를 잠그면 로그인 자체가 불가능해진다.
 *
 * @EnableMethodSecurity는 여기가 아니라 ServletConfig와 RootConfig에 있다. 이 애노테이션은
 * '자기가 속한 컨텍스트의 빈'에만 프록시를 걸기 때문에, @PreAuthorize가 붙는 쪽에 있어야 한다.
 * 지금 @PreAuthorize는 컨트롤러(자식)에만 있어서 실제로 일하는 것은 ServletConfig 쪽이다.
 * RootConfig 쪽은 서비스에 @PreAuthorize를 붙이는 날을 대비한 안전장치다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 인증 없이 접근 가능한 경로.
     * ServletConfig가 @RestController에 /api/v1 접두사를 붙이므로 여기도 전체 경로로 적는다.
     */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/signup",    // 회원가입 — 토큰이 있을 수 없다
            "/api/v1/auth/login",     // 로그인 — 토큰을 받으러 오는 곳
            "/api/v1/auth/reissue",   // 재발급 — Access가 만료된 상태로 온다
            "/api/v1/auth/logout",
            "/api/v1/auth/csrf",
            "/api/v1/auth/check-email", // 이메일 중복 확인 - 가입 전이라 토큰이 없다
            "/api/v1/auth/phone-verification/send",
            "/api/v1/auth/legal-guardian-verification/send", // [보호자 가입 흐름 1] 가입 전 SMS 발송 API
            "/api/v1/auth/legal-guardian-verification/confirm", // [보호자 가입 흐름 3] 가입 전 동의 토큰 발급 API
            "/api/v1/health",         // 헬스체크 — 모니터링이 토큰 없이 호출
            "/api/v1/health/**",
            "/api/v1/terms",          // 약관 조회 — 가입 전에도 봐야 하는 문서다
            "/api/v1/terms/**",

            // SSE 구독 — EventSource가 Authorization 헤더를 붙일 수 없어 1회용 티켓으로 인증한다.
            // 여기 넣는 이유는 JwtAuthenticationFilter가 막아서가 아니다. 그 필터는 헤더가 없으면
            // 아무 것도 하지 않고 통과시킨다. 걸리는 것은 아래 anyRequest().authenticated() 다.
            // 신원 확인은 SseController가 티켓을 소비하며 직접 하고, 실패하면 401을 던진다.
            // 티켓 발급(POST /api/v1/sse/ticket)은 넣지 않는다 — 그쪽은 정상 Bearer 인증을 받는다.
            "/api/v1/sse/subscribe",

            // Swagger (springfox 2.9.2) — UI 자체 + UI가 로드하는 정적 리소스 + 스펙 JSON
            "/swagger-ui.html",
            "/webjars/**",
            "/swagger-resources",
            "/swagger-resources/**",
            "/v2/api-docs"
    };

    /**
     * 공개 경로를 AntPathRequestMatcher로 직접 만든다.
     *
     * requestMatchers(String...)를 쓰면 Spring Security가 MvcRequestMatcher를 만들려 하고,
     * 그건 @EnableWebMvc가 등록하는 mvcHandlerMappingIntrospector 빈을 요구한다.
     * 그 빈은 ServletConfig(자식 컨텍스트)에 있고 SecurityConfig는 루트 컨텍스트라
     * 볼 수 없다 → 앱 기동 시 NoSuchBeanDefinitionException.
     *
     * 경로 매칭 방식(Ant)을 명시하면 MVC 컨텍스트에 의존하지 않는다.
     */
    private static RequestMatcher[] publicMatchers() {
        return Arrays.stream(PUBLIC_ENDPOINTS)
                .map(pattern -> (RequestMatcher) new AntPathRequestMatcher(pattern))
                .toArray(RequestMatcher[]::new);
    }

    private static RequestMatcher csrfMatcher() {
        return new OrRequestMatcher(
                new AntPathRequestMatcher("/api/v1/auth/login", "POST"),
                new AntPathRequestMatcher("/api/v1/auth/reissue", "POST"),
                new AntPathRequestMatcher("/api/v1/auth/logout", "POST"));
    }

    // application.properties의 jwt.* 값. 기본값이 있어 JWT_SECRET 없이도 앱이 기동한다.
    // 배포에서는 환경변수로 반드시 override 해야 한다(저장소에 공개된 기본값이므로).
    @Value("${jwt.secret}")
    private String jwtSecret;
    @Value("${jwt.access-expiration}")
    private long accessExpirationMs;
    @Value("${jwt.refresh-expiration}")
    private long refreshExpirationMs;

    @Bean
    public JwtProvider jwtProvider() {
        return new JwtProvider(jwtSecret, accessExpirationMs, refreshExpirationMs);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            RefreshTokenStore refreshTokenStore) {
        // @Configuration 클래스는 CGLIB 프록시라 jwtProvider()를 직접 호출해도
        // 새 인스턴스가 아니라 위에서 만든 싱글턴 빈이 반환된다.
        return new JwtAuthenticationFilter(jwtProvider(), refreshTokenStore);
    }

    @Bean
    public RestAuthenticationEntryPoint restAuthenticationEntryPoint() {
        return new RestAuthenticationEntryPoint();
    }

    @Bean
    public RestAccessDeniedHandler restAccessDeniedHandler() {
        return new RestAccessDeniedHandler();
    }

    /**
     * 비밀번호 해시. 회원가입/로그인이 쓴다.
     * BCrypt 해시는 60자이므로 T_MBR_INFO_M.password VARCHAR(255)에 들어간다.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
                // CSRF는 csrfMatcher()가 고른 세 경로에만 건다. 나머지 API는 Authorization
                // 헤더로 인증하므로 브라우저가 자동으로 실어보낼 게 없어 CSRF가 성립하지 않는다.
                //   reissue·logout : Refresh Token 쿠키를 '읽어서' 동작한다. 쿠키는 타 사이트
                //                    요청에도 자동으로 붙으므로 여기가 CSRF의 본진이다.
                //   login          : 쿠키를 읽지 않고 발급만 한다. 막으려는 건 로그인 CSRF다 —
                //                    피해자를 공격자 계정으로 강제 로그인시켜 이후 활동을 그쪽에 쌓는 공격.
                // withHttpOnlyFalse는 FE가 JS로 토큰을 읽어 헤더에 다시 실어야 하기 때문이다.
                // (GET /api/v1/auth/csrf 가 그 토큰을 내려준다.)
                .csrf(csrf -> csrf
                        .csrfTokenRepository(
                                CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .requireCsrfProtectionMatcher(csrfMatcher()))
                // 세션을 만들지 않는다. 인증 상태를 서버가 들고 있지 않고 매 요청 토큰으로 판단한다.
                // 이게 JwtProvider가 필요했던 근본 이유다.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 인가 판단보다 먼저 실행되어야 SecurityContext가 채워진 상태로 인가가 돌아간다.
                // UsernamePasswordAuthenticationFilter는 폼 로그인용 필터로, 인가 판단 앞에 있어
                // 그 앞에 끼우면 순서가 맞는다(우리는 폼 로그인을 쓰지 않는다).
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class)
                // 인가가 요청을 거부했을 때 응답을 만드는 두 담당자.
                // 여기 등록하지 않으면 Spring Security 기본 동작(HTML 오류 페이지 등)이 나가
                // ApiResponse 형식이 깨진다.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(restAuthenticationEntryPoint())  // 401
                        .accessDeniedHandler(restAccessDeniedHandler()))           // 403
                // 순서가 중요하다. 위에서 아래로 평가하므로 화이트리스트가 먼저 와야 한다.
                // anyRequest()를 먼저 두면 모든 요청이 거기 걸려 화이트리스트가 무시된다.
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(publicMatchers()).permitAll()
                        .anyRequest().authenticated());

        return http.build();
    }
}
