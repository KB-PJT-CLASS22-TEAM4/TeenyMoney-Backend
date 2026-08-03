package com.teenyfin.teenymoney.domain.auth.controller;

import com.teenyfin.teenymoney.domain.auth.dto.request.LoginRequestDTO;
import com.teenyfin.teenymoney.domain.auth.dto.request.PhoneVerificationSendRequestDTO;
import com.teenyfin.teenymoney.domain.auth.dto.request.SignupRequestDTO;
import com.teenyfin.teenymoney.domain.auth.dto.response.EmailAvailabilityResponseDTO;
import com.teenyfin.teenymoney.domain.auth.dto.response.CsrfTokenResponseDTO;
import com.teenyfin.teenymoney.domain.auth.dto.response.LoginResponseDTO;
import com.teenyfin.teenymoney.domain.auth.dto.response.SignupResponseDTO;
import com.teenyfin.teenymoney.domain.auth.dto.response.TokenReissueResponseDTO;
import com.teenyfin.teenymoney.domain.auth.service.AuthService;
import com.teenyfin.teenymoney.domain.auth.service.LoginResult;
import com.teenyfin.teenymoney.domain.auth.service.PhoneVerificationService;
import com.teenyfin.teenymoney.domain.auth.service.TokenReissueResult;
import com.teenyfin.teenymoney.global.auth.CookieUtil;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

// HTTP 요청을 받는 진입점
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final PhoneVerificationService phoneVerificationService;
    private final CookieUtil cookieUtil;

    public AuthController(
            AuthService authService,
            PhoneVerificationService phoneVerificationService,
            CookieUtil cookieUtil) {
        this.authService = authService;
        this.phoneVerificationService = phoneVerificationService;
        this.cookieUtil = cookieUtil;
    }

    @PostMapping("/phone-verification/send")
    public ApiResponse<Void> sendPhoneVerification(
            @Valid @RequestBody PhoneVerificationSendRequestDTO request) {
        phoneVerificationService.sendCode(request.getPhoneNumber());
        return ApiResponse.ok();
    }

    @GetMapping("/check-email")
    public ApiResponse<EmailAvailabilityResponseDTO> checkEmail(
            @RequestParam String email) {
        return ApiResponse.ok(EmailAvailabilityResponseDTO.of(
                authService.isEmailAvailable(email)));
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponseDTO>> signup(
            @Valid @RequestBody SignupRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(authService.signup(request)));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponseDTO> login(
            @Valid @RequestBody LoginRequestDTO request,
            HttpServletResponse response) {
        LoginResult result = authService.login(request);
        cookieUtil.addRefreshCookie(response, result.refreshToken());
        return ApiResponse.ok(result.toResponse());
    }

    @GetMapping("/csrf")
    public ApiResponse<CsrfTokenResponseDTO> csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return ApiResponse.ok(new CsrfTokenResponseDTO(token.getToken()));
    }

    @PostMapping("/reissue")
    public ApiResponse<TokenReissueResponseDTO> reissue(
            HttpServletRequest request,
            HttpServletResponse response) {
        TokenReissueResult result = authService.reissue(
                cookieUtil.readRefreshToken(request));
        cookieUtil.addRefreshCookie(response, result.refreshToken());
        return ApiResponse.ok(TokenReissueResponseDTO.of(result.accessToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
            String authorization,
            HttpServletRequest request,
            HttpServletResponse response) {
        try {
            authService.logout(
                    bearerToken(authorization),
                    cookieUtil.readRefreshToken(request));
            return ApiResponse.ok();
        } finally {
            cookieUtil.clearRefreshCookie(response);
        }
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring("Bearer ".length());
        return token.isBlank() ? null : token;
    }
}
