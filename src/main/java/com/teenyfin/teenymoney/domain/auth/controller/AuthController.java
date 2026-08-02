package com.teenyfin.teenymoney.domain.auth.controller;

import com.teenyfin.teenymoney.domain.auth.dto.request.PhoneVerificationSendRequestDTO;
import com.teenyfin.teenymoney.domain.auth.dto.request.SignupRequestDTO;
import com.teenyfin.teenymoney.domain.auth.dto.response.EmailAvailabilityResponseDTO;
import com.teenyfin.teenymoney.domain.auth.dto.response.SignupResponseDTO;
import com.teenyfin.teenymoney.domain.auth.service.AuthService;
import com.teenyfin.teenymoney.domain.auth.service.PhoneVerificationService;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final PhoneVerificationService phoneVerificationService;

    public AuthController(
            AuthService authService,
            PhoneVerificationService phoneVerificationService) {
        this.authService = authService;
        this.phoneVerificationService = phoneVerificationService;
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
}
