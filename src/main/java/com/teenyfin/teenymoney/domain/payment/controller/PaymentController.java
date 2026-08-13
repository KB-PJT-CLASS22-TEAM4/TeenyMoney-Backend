package com.teenyfin.teenymoney.domain.payment.controller;

import com.teenyfin.teenymoney.domain.payment.dto.request.PaymentQrRequestDTO;
import com.teenyfin.teenymoney.domain.payment.dto.request.PaymentRequestDTO;
import com.teenyfin.teenymoney.domain.payment.dto.response.PaymentQrResponseDTO;
import com.teenyfin.teenymoney.domain.payment.dto.response.PaymentResponseDTO;
import com.teenyfin.teenymoney.domain.payment.service.PaymentService;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@Api(tags = "결제")
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @ApiOperation(value = "QR 코드 검증", notes = "유효한 QR 코드인지 검증하고 결제 정보를 반환합니다. 주의 단계일 경우 최근 30일 내에 소비한 횟수와 금액을 함께 반환합니다.")
    @PostMapping("/qrcode")
    public ApiResponse<PaymentQrResponseDTO> getPaymentInfo(
            @AuthenticationPrincipal MemberPrincipal memberPrincipal,
            @RequestBody @Valid PaymentQrRequestDTO paymentQrRequestDTO) {
        return ApiResponse.ok(paymentService.getPaymentInfo(memberPrincipal.memberId(), paymentQrRequestDTO));
    }

    @ApiOperation(value = "결제 진행", notes = "비밀번호를 입력받아 실제 결제를 진행합니다.")
    @PostMapping
    public ApiResponse<PaymentResponseDTO> progressPayment(
            @AuthenticationPrincipal MemberPrincipal memberPrincipal,
            @RequestBody @Valid PaymentRequestDTO paymentRequestDTO) {
        return ApiResponse.ok(paymentService.progressPayment(memberPrincipal.memberId(), paymentRequestDTO));
    }
}
