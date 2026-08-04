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
 * Task 1~3에서 만든 조각들을 여기서 처음 조립한다. 그 전까지는 클래스만 존재하고
 * 실제 요청에는 아무 영향이 없었다.
 *   JwtProvider              토큰 발급·검증        (Task 1)
 *   JwtAuthenticationFilter  매 요청 인증          (Task 2)
 *   Rest*EntryPoint/Handler  401/403 JSON 응답     (Task 3)
 *
 * 보안 빈은 반드시 여기 @Bean으로 등록한다. @Component로 두면 ServletConfig가
 * global 패키지를 자식 컨텍스트로 스캔해 빈을 만들고, 필터체인(루트)이 그걸 못 봐서
 * 필터가 체인에 안 붙는다. 예외도 로그도 없이 인증이 통째로 동작하지 않는다.
 *
 * 인가 규칙은 '공개 경로 화이트리스트 + 나머지 전부 인증'이다. 화이트리스트에는
 * 토큰을 아직 못 받았거나 받을 수 없는 상태에서 호출해야 하는 경로만 넣는다.
 * 여기를 잠그면 로그인 자체가 불가능해진다.
 *
 * @EnableMethodSecurity는 여기가 아니라 ServletConfig(자식 컨텍스트)에 있다.
 * @PreAuthorize를 붙일 컨트롤러가 그쪽에 살기 때문이다. 자세한 이유는 ServletConfig 주석 참고.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 인증 없이 접근 가능한 경로.
     * ServletConfig가 @RestController에 /api/v1 접두사를 붙이므로 여기도 전체 경로로 적는다.
     */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/signup",    // 회원가입 — 토큰이 있을 수 없다 (하위3)
            "/api/v1/auth/login",     // 로그인 — 토큰을 받으러 오는 곳 (하위3)
            "/api/v1/auth/reissue",   // 재발급 — Access가 만료된 상태로 온다 (하위4)
            "/api/v1/auth/logout",
            "/api/v1/auth/csrf",
            "/api/v1/auth/check-email", // 이메일 중복 확인 - 가입 전이라 토큰이 없다 (하위3)
            "/api/v1/auth/phone-verification/send",
            "/api/v1/health",         // 헬스체크 — 모니터링이 토큰 없이 호출
            "/api/v1/health/**",

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
     * 비밀번호 해시. 회원가입/로그인(하위3)이 쓴다.
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
                // REST API는 브라우저 폼 기반이 아니라 토큰으로 인증하므로 CSRF 토큰이 불필요하다.
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
