package com.teenyfin.teenymoney.global.security;

import com.teenyfin.teenymoney.config.SecurityConfig;
import com.teenyfin.teenymoney.global.auth.RefreshTokenStore;
import com.teenyfin.teenymoney.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * 인증 파이프라인을 HTTP 레벨에서 검증한다.
 *
 * Task 1~3의 단위 테스트는 각 클래스를 따로 확인했다. 이 테스트는 필터·인가 규칙·
 * 진입점·핸들러가 실제 요청 하나에서 함께 동작하는지를 본다. 단위 테스트가 모두
 * 통과해도 배선이 틀리면(필터 위치, 화이트리스트 순서 등) 여기서만 드러난다.
 *
 * TestWebConfig가 운영의 ServletConfig 역할을 한다. @EnableMethodSecurity를 여기 두는
 * 이유도 같다 — @PreAuthorize를 붙인 컨트롤러가 이 설정에 등록되기 때문이다.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = {
        SecurityConfig.class,
        JwtSecurityIntegrationTest.TestWebConfig.class
})
@TestPropertySource(properties = {
        "jwt.secret=b5ZbpsxM9qd3XTR09Hm/tTbpmTybNbUp/Qc51yyPmk4=",
        "jwt.access-expiration=1800000",
        "jwt.refresh-expiration=1209600000"
})
class JwtSecurityIntegrationTest {

    private static final String PROTECTED = "/api/v1/test/security/protected";
    private static final String PARENT_ONLY = "/api/v1/test/security/parent";
    private static final String PUBLIC = "/api/v1/auth/login";
    private static final String PHONE_VERIFICATION = "/api/v1/auth/phone-verification/send";
    private static final String MEMBER_ME = "/api/v1/members/me";
    private static final String GENERATION = "generation-17";
    private static final String REISSUE = "/api/v1/auth/reissue";
    private static final String LOGOUT = "/api/v1/auth/logout";
    private static final String CSRF = "/api/v1/auth/csrf";

    @Configuration
    @EnableWebMvc
    @EnableMethodSecurity   // 운영에서는 ServletConfig가 담당한다
    static class TestWebConfig {
        @Bean
        TestProtectedController testProtectedController() {
            return new TestProtectedController();
        }

        @Bean
        RefreshTokenStore refreshTokenStore() {
            return mock(RefreshTokenStore.class);
        }
    }

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reset(refreshTokenStore);
        when(refreshTokenStore.findGeneration(anyLong())).thenReturn(GENERATION);
        // springSecurity()를 적용해야 필터체인이 MockMvc 요청에 붙는다.
        // 빼먹으면 인가 규칙이 동작하지 않아 모든 테스트가 200을 받는다.
        mockMvc = webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("정상 Access Token으로 보호 API에 접근하고 MemberPrincipal이 주입된다")
    void validAccessTokenReachesControllerWithPrincipal() throws Exception {
        HttpServletResponse response = call(PROTECTED,
                jwtProvider.createAccessToken(17L, "PARENT", GENERATION));
        String body = bodyOf(response);

        show("status", response.getStatus(), 200);
        show("body", body, null);

        assertEquals(200, response.getStatus(), body);
        // 컨트롤러가 @AuthenticationPrincipal로 받은 값이 그대로 응답에 실렸다.
        assertTrue(body.contains("\"memberId\":17"), body);
        assertTrue(body.contains("\"role\":\"PARENT\""), body);
    }

    @Test
    @DisplayName("토큰 없이 보호 API를 호출하면 401 AUTH_UNAUTHORIZED JSON을 받는다")
    void noTokenReturnsUnauthorized() throws Exception {
        assertErrorResponse(PROTECTED, null, 401, "AUTH_UNAUTHORIZED");
    }

    @Test
    @DisplayName("서명이 잘못된 토큰은 401 AUTH_TOKEN_INVALID를 받는다")
    void tamperedTokenReturnsUnauthorized() throws Exception {
        // 다른 키로 서명한 토큰. 형식은 완벽하지만 우리 키로는 검증되지 않는다.
        String forged = new JwtProvider("nDlRA4wqNsD9UWmGExA1MCPvrWiVob6ewIO9ss319jY=", 1_800_000L, 1_209_600_000L)
                .createAccessToken(17L, "PARENT", GENERATION);

        assertErrorResponse(PROTECTED, forged, 401, "AUTH_TOKEN_INVALID");
    }

    @Test
    @DisplayName("만료된 Access Token은 401 AUTH_TOKEN_EXPIRED를 받는다")
    void expiredTokenReturnsUnauthorized() throws Exception {
        String expired = new JwtProvider("b5ZbpsxM9qd3XTR09Hm/tTbpmTybNbUp/Qc51yyPmk4=", -60_000L, -60_000L)
                .createAccessToken(17L, "CHILD", GENERATION);

        assertErrorResponse(PROTECTED, expired, 401, "AUTH_TOKEN_EXPIRED");
    }

    @Test
    @DisplayName("tokenType=REFRESH 토큰으로 보호 API를 호출하면 401 AUTH_TOKEN_INVALID를 받는다")
    void refreshTokenIsRejected() throws Exception {
        // 서명도 유효하고 만료도 안 됐다. tokenType 검사만이 이걸 막는다.
        assertErrorResponse(PROTECTED,
                jwtProvider.createRefreshToken(17L, GENERATION),
                401, "AUTH_TOKEN_INVALID");
    }

    @Test
    @DisplayName("CHILD 토큰으로 부모 전용 API를 호출하면 403 AUTH_FORBIDDEN을 받는다")
    void childCannotAccessParentOnlyEndpoint() throws Exception {
        assertErrorResponse(PARENT_ONLY,
                jwtProvider.createAccessToken(17L, "CHILD", GENERATION),
                403, "AUTH_FORBIDDEN");
    }

    @Test
    @DisplayName("PARENT 토큰으로 부모 전용 API를 호출하면 성공한다")
    void parentCanAccessParentOnlyEndpoint() throws Exception {
        HttpServletResponse response = call(PARENT_ONLY,
                jwtProvider.createAccessToken(17L, "PARENT", GENERATION));
        String body = bodyOf(response);

        show("status", response.getStatus(), 200);
        show("body", body, null);

        assertEquals(200, response.getStatus(), body);
    }

    @Test
    @DisplayName("화이트리스트 경로는 토큰 없이 접근된다")
    void publicEndpointNeedsNoToken() throws Exception {
        HttpServletResponse response = call(PUBLIC, null);

        show("status", response.getStatus(), 200);

        assertEquals(200, response.getStatus(), bodyOf(response));
    }

    @Test
    @DisplayName("화이트리스트 경로는 만료된 토큰이 실려와도 통과한다")
    void publicEndpointIgnoresExpiredToken() throws Exception {
        // 앱이 저장된 옛 토큰을 모든 요청에 자동으로 붙이는 상황.
        // 필터가 만료를 보고 즉시 401을 냈다면 로그인 자체가 불가능해진다.
        String expired = new JwtProvider("b5ZbpsxM9qd3XTR09Hm/tTbpmTybNbUp/Qc51yyPmk4=", -60_000L, -60_000L)
                .createAccessToken(17L, "PARENT", GENERATION);

        HttpServletResponse response = call(PUBLIC, expired);

        show("status", response.getStatus(), 200);

        assertEquals(200, response.getStatus(), bodyOf(response));
    }

    @Test
    void phoneVerificationEndpointNeedsNoToken() throws Exception {
        HttpServletResponse response = mockMvc.perform(post(PHONE_VERIFICATION))
                .andReturn().getResponse();

        assertEquals(200, response.getStatus(), bodyOf(response));
    }

    @Test
    void memberMeEndpointNeedsAccessToken() throws Exception {
        assertErrorResponse(MEMBER_ME, null, 401, "AUTH_UNAUTHORIZED");
    }

    @Test
    void memberMeEndpointAcceptsAccessToken() throws Exception {
        HttpServletResponse response = call(
                MEMBER_ME, jwtProvider.createAccessToken(17L, "PARENT", GENERATION));

        assertEquals(200, response.getStatus(), bodyOf(response));
    }

    @Test
    void cookieAuthPostsRequireCsrfToken() throws Exception {
        for (String path : new String[]{PUBLIC, REISSUE, LOGOUT}) {
            HttpServletResponse rejected = mockMvc.perform(post(path))
                    .andReturn().getResponse();
            assertEquals(403, rejected.getStatus(), bodyOf(rejected));
            assertTrue(bodyOf(rejected).contains("\"code\":\"AUTH_FORBIDDEN\""),
                    bodyOf(rejected));

            HttpServletResponse accepted = mockMvc.perform(post(path).with(csrf()))
                    .andReturn().getResponse();
            assertEquals(200, accepted.getStatus(), bodyOf(accepted));
        }
    }

    @Test
    void csrfEndpointIsPublicAndIssuesCookie() throws Exception {
        HttpServletResponse response = mockMvc.perform(get(CSRF))
                .andReturn().getResponse();

        assertEquals(200, response.getStatus(), bodyOf(response));
        assertTrue(bodyOf(response).contains("\"success\":true"), bodyOf(response));
        assertTrue(response.getHeader(HttpHeaders.SET_COOKIE).contains("XSRF-TOKEN="));
    }

    // --- 도우미 -------------------------------------------------------------

    private void assertErrorResponse(String path, String token, int expectedStatus, String expectedCode)
            throws Exception {
        HttpServletResponse response = call(path, token);
        String body = bodyOf(response);

        show("status", response.getStatus(), expectedStatus);
        show("body", body, null);

        assertEquals(expectedStatus, response.getStatus(), body);
        assertTrue(body.contains("\"success\":false"), body);
        assertTrue(body.contains("\"code\":\"" + expectedCode + "\""), body);
    }

    private HttpServletResponse call(String path, String token) throws Exception {
        MockHttpServletRequestBuilder request = get(path);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn().getResponse();
    }

    private String bodyOf(HttpServletResponse response) throws Exception {
        return ((org.springframework.mock.web.MockHttpServletResponse) response)
                .getContentAsString(StandardCharsets.UTF_8);
    }

    // 통과 여부만이 아니라 실제 응답을 눈으로 확인하기 위한 출력이다.
    private static void show(String label, Object actual, Object expected) {
        String suffix = (expected == null) ? "" : "   (기대 " + expected + ")";
        System.out.printf("      %-8s: %s%s%n", label, actual, suffix);
    }
}
