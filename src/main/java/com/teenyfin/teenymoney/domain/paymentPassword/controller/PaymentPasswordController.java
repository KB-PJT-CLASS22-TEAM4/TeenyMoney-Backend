package com.teenyfin.teenymoney.domain.paymentPassword.controller;

import com.teenyfin.teenymoney.domain.paymentPassword.dto.request.PaymentPasswordRequestDTO;
import com.teenyfin.teenymoney.domain.paymentPassword.service.PaymentPasswordService;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@Api(tags = "결제 비밀번호")
@RestController
@RequestMapping("/members/me/payment-password")
@RequiredArgsConstructor
public class PaymentPasswordController {

    private final PaymentPasswordService paymentPasswordService;

    @ApiOperation(value = "결제 비밀번호 최초 등록", notes = "결제 비밀번호를 최초 등록합니다.")
    @PostMapping
    public ApiResponse<Void> setPaymentPassword(
            @AuthenticationPrincipal MemberPrincipal memberPrincipal,
            @RequestBody @Valid PaymentPasswordRequestDTO paymentPasswordRequestDTO) {
        paymentPasswordService.setPaymentPassword(memberPrincipal.memberId(), paymentPasswordRequestDTO);
        return ApiResponse.ok();
    }
}
