package com.teenyfin.teenymoney.domain.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.teenyfin.teenymoney.domain.auth.dto.request.LoginRequestDTO;
import com.teenyfin.teenymoney.domain.auth.dto.request.SignupRequestDTO;
import com.teenyfin.teenymoney.domain.auth.dto.response.SignupResponseDTO;
import com.teenyfin.teenymoney.domain.auth.exception.AuthErrorCode;
import com.teenyfin.teenymoney.domain.auth.service.AuthService;
import com.teenyfin.teenymoney.domain.auth.service.LegalGuardianVerificationService;
import com.teenyfin.teenymoney.domain.auth.service.LoginResult;
import com.teenyfin.teenymoney.domain.auth.service.PhoneVerificationService;
import com.teenyfin.teenymoney.domain.auth.service.TokenReissueResult;
import com.teenyfin.teenymoney.global.auth.CookieUtil;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.exception.CommonErrorCode;
import com.teenyfin.teenymoney.global.exception.GlobalExceptionAdvice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.nio.charset.StandardCharsets;

import javax.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class AuthControllerTest {

    private AuthService authService;
    private PhoneVerificationService phoneVerificationService;
    private LegalGuardianVerificationService legalGuardianVerificationService;
    private CookieUtil cookieUtil;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        phoneVerificationService = mock(PhoneVerificationService.class);
        legalGuardianVerificationService = mock(LegalGuardianVerificationService.class);
        cookieUtil = mock(CookieUtil.class);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AuthController(
                                authService,
                                phoneVerificationService,
                                legalGuardianVerificationService,
                                cookieUtil))
                .setControllerAdvice(new GlobalExceptionAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void sendPhoneVerificationReturnsSuccessEnvelope() throws Exception {
        var response = mockMvc.perform(post("/auth/phone-verification/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"010-1234-5678\"}"))
                .andReturn().getResponse();

        assertEquals(200, response.getStatus());
        assertBodyContains(response, "\"success\":true", "\"code\":\"OK\"");

        verify(phoneVerificationService).sendCode("010-1234-5678");
    }

    @Test
    void legalGuardianVerificationSendUsesLegalGuardianPhoneNumber() throws Exception {
        var response = mockMvc.perform(post("/auth/legal-guardian-verification/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"010-1234-5678\"}"))
                .andReturn().getResponse();

        assertEquals(200, response.getStatus());
        verify(legalGuardianVerificationService).sendCode("010-1234-5678");
    }

    @Test
    void legalGuardianVerificationConfirmReturnsOneTimeConsentToken() throws Exception {
        when(legalGuardianVerificationService.confirm(
                "김보호", "MOTHER", "010-1234-5678", "123456", "1.0", "1.0"))
                .thenReturn("legal-guardian-token");

        var response = mockMvc.perform(post("/auth/legal-guardian-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"
                                + "\"legalGuardianName\":\"김보호\","
                                + "\"relationship\":\"MOTHER\","
                                + "\"phoneNumber\":\"010-1234-5678\","
                                + "\"verificationCode\":\"123456\","
                                + "\"legalGuardianTermsAgreed\":true,"
                                + "\"serviceTermsVersion\":\"1.0\","
                                + "\"privacyTermsVersion\":\"1.0\""
                                + "}"))
                .andReturn().getResponse();

        assertEquals(200, response.getStatus());
        assertBodyContains(response, "\"legalGuardianConsentToken\":\"legal-guardian-token\"");
    }

    @Test
    void checkEmailReturnsAvailability() throws Exception {
        when(authService.isEmailAvailable("user@example.com")).thenReturn(true);

        var response = mockMvc.perform(get("/auth/check-email")
                        .param("email", "user@example.com"))
                .andReturn().getResponse();

        assertEquals(200, response.getStatus());
        assertBodyContains(response, "\"available\":true");
    }

    @Test
    void signupAcceptsIsoBirthDateAndReturnsCreatedMember() throws Exception {
        when(authService.signup(any(SignupRequestDTO.class)))
                .thenReturn(SignupResponseDTO.of(17L));

        var response = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSignupJson()))
                .andReturn().getResponse();

        assertEquals(201, response.getStatus());
        assertBodyContains(response, "\"memberId\":17");
        assertFalse(response.getContentAsString(StandardCharsets.UTF_8).contains("\"role\""));

        ArgumentCaptor<SignupRequestDTO> captor = ArgumentCaptor.forClass(SignupRequestDTO.class);
        verify(authService).signup(captor.capture());
        assertEquals(LocalDate.of(2012, 3, 4), captor.getValue().getBirthDate());
    }

    @Test
    void signupMissingFieldsReturnsFieldValidationErrors() throws Exception {
        var response = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn().getResponse();

        assertEquals(400, response.getStatus());
        assertBodyContains(response,
                "\"code\":\"COMMON_INVALID_INPUT\"",
                "\"name\"", "\"birthDate\"", "\"phoneNumber\"",
                "\"verificationCode\"", "\"email\"", "\"password\"");
    }

    @Test
    void malformedBirthDateReturnsMalformedJsonError() throws Exception {
        var response = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSignupJson().replace("2012-03-04", "03/04/2012")))
                .andReturn().getResponse();

        assertEquals(400, response.getStatus());
        assertBodyContains(response, "\"code\":\"COMMON_MALFORMED_JSON\"");
    }

    @Test
    void businessErrorKeepsItsStatusAndCode() throws Exception {
        doThrow(new BusinessException(AuthErrorCode.AUTH_SMS_TOO_MANY_REQUESTS))
                .when(phoneVerificationService).sendCode(any());

        var response = mockMvc.perform(post("/auth/phone-verification/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"01012345678\"}"))
                .andReturn().getResponse();

        assertEquals(429, response.getStatus());
        assertBodyContains(response, "\"code\":\"AUTH_SMS_TOO_MANY_REQUESTS\"");
    }

    @Test
    void passwordMustContainLetterDigitAndSpecialCharacter() throws Exception {
        var response = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSignupJson().replace("Password1!", "Password123")))
                .andReturn().getResponse();

        assertEquals(400, response.getStatus());
        assertBodyContains(response, "\"password\"");
    }

    @Test
    void loginReturnsAccessTokenAndMemberSummaryWithoutRefreshTokenInBody() throws Exception {
        when(authService.login(any(LoginRequestDTO.class))).thenReturn(new LoginResult(
                "access-token", "refresh-token", 17L, "PARENT", "Test User"));

        var response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validLoginJson()))
                .andReturn().getResponse();

        assertEquals(200, response.getStatus());
        assertBodyContains(response,
                "\"accessToken\":\"access-token\"",
                "\"memberId\":17",
                "\"role\":\"PARENT\"",
                "\"name\":\"Test User\"");
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertFalse(body.contains("refreshToken"), body);
        assertFalse(body.contains("refresh-token"), body);
        verify(cookieUtil).addRefreshCookie(
                any(HttpServletResponse.class), eq("refresh-token"));
    }

    @Test
    void loginInvalidRequestReturnsValidationErrors() throws Exception {
        var response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"\"}"))
                .andReturn().getResponse();

        assertEquals(400, response.getStatus());
        assertBodyContains(response,
                "\"code\":\"COMMON_INVALID_INPUT\"",
                "\"email\"", "\"password\"");
    }

    @Test
    void loginTrimsEmailBeforeValidation() throws Exception {
        when(authService.login(any(LoginRequestDTO.class))).thenReturn(new LoginResult(
                "access-token", "refresh-token", 17L, "PARENT", "Test User"));

        var response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validLoginJson().replace(
                                "user@example.com", "  USER@Example.COM  ")))
                .andReturn().getResponse();

        assertEquals(200, response.getStatus());
        ArgumentCaptor<LoginRequestDTO> captor =
                ArgumentCaptor.forClass(LoginRequestDTO.class);
        verify(authService).login(captor.capture());
        assertEquals("USER@Example.COM", captor.getValue().getEmail());
    }

    @Test
    void loginBusinessErrorKeepsUnauthorizedStatusAndCode() throws Exception {
        when(authService.login(any(LoginRequestDTO.class)))
                .thenThrow(new BusinessException(AuthErrorCode.AUTH_INVALID_CREDENTIALS));

        var response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validLoginJson()))
                .andReturn().getResponse();

        assertEquals(401, response.getStatus());
        assertBodyContains(response, "\"code\":\"AUTH_INVALID_CREDENTIALS\"");
    }

    @Test
    void csrfReturnsTokenForFrontendHeader() throws Exception {
        var response = mockMvc.perform(get("/auth/csrf")
                        .requestAttr(CsrfToken.class.getName(),
                                new DefaultCsrfToken(
                                        "X-XSRF-TOKEN", "_csrf", "csrf-token")))
                .andReturn().getResponse();

        assertEquals(200, response.getStatus());
        assertBodyContains(response, "\"token\":\"csrf-token\"");
    }

    @Test
    void reissueReturnsAccessTokenAndRotatesRefreshCookie() throws Exception {
        when(cookieUtil.readRefreshToken(any())).thenReturn("old-refresh");
        when(authService.reissue("old-refresh"))
                .thenReturn(new TokenReissueResult("new-access", "new-refresh"));

        var response = mockMvc.perform(post("/auth/reissue"))
                .andReturn().getResponse();

        assertEquals(200, response.getStatus());
        assertBodyContains(response, "\"accessToken\":\"new-access\"");
        assertFalse(response.getContentAsString(StandardCharsets.UTF_8)
                .contains("new-refresh"));
        verify(cookieUtil).addRefreshCookie(
                any(HttpServletResponse.class), eq("new-refresh"));
    }

    @Test
    void logoutPassesBearerAndRefreshThenClearsCookie() throws Exception {
        when(cookieUtil.readRefreshToken(any())).thenReturn("refresh-token");

        var response = mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer access-token"))
                .andReturn().getResponse();

        assertEquals(200, response.getStatus());
        verify(authService).logout("access-token", "refresh-token");
        verify(cookieUtil).clearRefreshCookie(any(HttpServletResponse.class));
    }

    @Test
    void logoutClearsCookieEvenWhenRedisFails() throws Exception {
        when(cookieUtil.readRefreshToken(any())).thenReturn("refresh-token");
        doThrow(new BusinessException(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE))
                .when(authService).logout(null, "refresh-token");

        var response = mockMvc.perform(post("/auth/logout"))
                .andReturn().getResponse();

        assertEquals(503, response.getStatus());
        verify(cookieUtil).clearRefreshCookie(any(HttpServletResponse.class));
    }

    private String validSignupJson() {
        return "{"
                + "\"name\":\"홍길동\","
                + "\"birthDate\":\"2012-03-04\","
                + "\"phoneNumber\":\"010-1234-5678\","
                + "\"verificationCode\":\"123456\","
                + "\"email\":\"user@example.com\","
                + "\"password\":\"Password1!\","
                + "\"passwordConfirm\":\"Password1!\","
                + "\"serviceTermsAgreed\":true,"
                + "\"privacyAgreed\":true,"
                + "\"serviceTermsVersion\":\"1.0\","
                + "\"privacyTermsVersion\":\"1.0\""
                + "}";
    }

    private String validLoginJson() {
        return "{"
                + "\"email\":\"user@example.com\","
                + "\"password\":\"password123\""
                + "}";
    }

    private void assertBodyContains(
            org.springframework.mock.web.MockHttpServletResponse response,
            String... fragments) throws Exception {
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        for (String fragment : fragments) {
            assertTrue(body.contains(fragment), body);
        }
    }
}
