package com.teenyfin.teenymoney.domain.auth.controller;

import com.teenyfin.teenymoney.domain.auth.dto.request.LoginRequestDTO;
import com.teenyfin.teenymoney.domain.auth.dto.request.LegalGuardianVerificationConfirmRequestDTO;
import com.teenyfin.teenymoney.domain.auth.dto.request.PhoneVerificationSendRequestDTO;
import com.teenyfin.teenymoney.domain.auth.dto.request.SignupRequestDTO;
import com.teenyfin.teenymoney.domain.auth.dto.response.EmailAvailabilityResponseDTO;
import com.teenyfin.teenymoney.domain.auth.dto.response.LegalGuardianConsentTokenResponseDTO;
import com.teenyfin.teenymoney.domain.auth.dto.response.CsrfTokenResponseDTO;
import com.teenyfin.teenymoney.domain.auth.dto.response.LoginResponseDTO;
import com.teenyfin.teenymoney.domain.auth.dto.response.SignupResponseDTO;
import com.teenyfin.teenymoney.domain.auth.dto.response.TokenReissueResponseDTO;
import com.teenyfin.teenymoney.domain.auth.service.AuthService;
import com.teenyfin.teenymoney.domain.auth.service.LegalGuardianVerificationService;
import com.teenyfin.teenymoney.domain.auth.service.LoginResult;
import com.teenyfin.teenymoney.domain.auth.service.PhoneVerificationService;
import com.teenyfin.teenymoney.domain.auth.service.TokenReissueResult;
import com.teenyfin.teenymoney.global.auth.CookieUtil;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponses;
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
@Api(tags = "Auth", description = "회원가입과 인증 API")
public class AuthController {

    private final AuthService authService;
    private final PhoneVerificationService phoneVerificationService;
    private final LegalGuardianVerificationService legalGuardianVerificationService;
    private final CookieUtil cookieUtil;

    public AuthController(
            AuthService authService,
            PhoneVerificationService phoneVerificationService,
            LegalGuardianVerificationService legalGuardianVerificationService,
            CookieUtil cookieUtil) {
        this.authService = authService;
        this.phoneVerificationService = phoneVerificationService;
        this.legalGuardianVerificationService = legalGuardianVerificationService;
        this.cookieUtil = cookieUtil;
    }

    @PostMapping("/phone-verification/send")
    @ApiOperation(
            value = "휴대폰 인증번호 발송",
            notes = "회원가입에 사용할 6자리 휴대폰 인증번호를 발송합니다.")
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "인증번호 발송 성공"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "휴대폰 번호 형식 오류"),
            @io.swagger.annotations.ApiResponse(code = 429, message = "인증번호 재요청 대기시간 미경과"),
            @io.swagger.annotations.ApiResponse(code = 503, message = "SMS 발송 불가")
    })
    public ApiResponse<Void> sendPhoneVerification(
            @Valid @RequestBody PhoneVerificationSendRequestDTO request) {
        phoneVerificationService.sendCode(request.getPhoneNumber());
        return ApiResponse.ok();
    }

    // 보호자 인증번호 발송 API
    @PostMapping("/legal-guardian-verification/send")
    @ApiOperation(
            value = "법정대리인 인증번호 발송",
            notes = "만 14세 미만 회원가입에 사용할 법정대리인 휴대폰 인증번호를 발송합니다.")
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "인증번호 발송 성공"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "입력값 오류"),
            @io.swagger.annotations.ApiResponse(code = 429, message = "인증번호 재요청 대기시간 미경과"),
            @io.swagger.annotations.ApiResponse(code = 503, message = "SMS 발송 불가")
    })
    public ApiResponse<Void> sendLegalGuardianVerification(
            @Valid @RequestBody PhoneVerificationSendRequestDTO request) {
        // [보호자 가입 흐름 1] 보호자 휴대폰 번호를 받아 SMS 인증번호 발송을 요청한다.
        legalGuardianVerificationService.sendCode(request.getPhoneNumber());
        return ApiResponse.ok();
    }

    // 보호자 인증 확인 및 토큰 발급 API
    @PostMapping("/legal-guardian-verification/confirm")
    @ApiOperation(
            value = "법정대리인 인증 확인",
            notes = "SMS 인증번호와 법정대리인 동의를 확인하고 10분간 유효한 일회용 동의 토큰을 발급합니다.")
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "인증 성공 및 동의 토큰 발급"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "입력값 또는 인증번호 오류"),
            @io.swagger.annotations.ApiResponse(code = 429, message = "인증번호 입력 횟수 초과")
    })
    public ApiResponse<LegalGuardianConsentTokenResponseDTO> confirmLegalGuardianVerification(
            @Valid @RequestBody LegalGuardianVerificationConfirmRequestDTO request) {
        // [보호자 가입 흐름 3] 인증번호와 보호자 정보·동의 약관 버전을 검증 서비스로 전달한다.
        String token = legalGuardianVerificationService.confirm(
                request.getLegalGuardianName(),
                request.getRelationship(),
                request.getPhoneNumber(),
                request.getVerificationCode(),
                request.getServiceTermsVersion(),
                request.getPrivacyTermsVersion());
        return ApiResponse.ok(new LegalGuardianConsentTokenResponseDTO(token));
    }

    @GetMapping("/check-email")
    @ApiOperation(value = "이메일 사용 가능 여부 확인")
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "확인 성공"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "이메일 형식 오류")
    })
    public ApiResponse<EmailAvailabilityResponseDTO> checkEmail(
            @ApiParam(value = "중복을 확인할 이메일", required = true, example = "user@example.com")
            @RequestParam String email) {
        return ApiResponse.ok(EmailAvailabilityResponseDTO.of(
                authService.isEmailAvailable(email)));
    }

    @PostMapping("/signup")
    @ApiOperation(
            value = "회원가입",
            notes = "만 14세 미만 가입자는 법정대리인 인증 확인 API에서 발급한 legalGuardianConsentToken이 필요합니다.")
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 201, message = "회원가입 성공"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "입력값·인증번호·약관 또는 법정대리인 동의 오류"),
            @io.swagger.annotations.ApiResponse(code = 409, message = "이메일 또는 휴대폰 번호 중복"),
            @io.swagger.annotations.ApiResponse(code = 429, message = "인증번호 입력 횟수 초과")
    })
    public ResponseEntity<ApiResponse<SignupResponseDTO>> signup(
            @Valid @RequestBody SignupRequestDTO request) {
        // [보호자 가입 흐름 8] 만 14세 미만이면 앞 단계에서 발급받은 legalGuardianConsentToken을 함께 받는다.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(authService.signup(request)));
    }

    @PostMapping("/login")
    @ApiOperation(
            value = "로그인",
            notes = "Access Token은 응답 본문으로, Refresh Token은 HttpOnly 쿠키로 발급합니다.")
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "로그인 성공"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "입력값 오류"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "이메일 또는 비밀번호 불일치"),
            @io.swagger.annotations.ApiResponse(code = 403, message = "비활성 회원"),
            @io.swagger.annotations.ApiResponse(code = 503, message = "인증 저장소 사용 불가")
    })
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
