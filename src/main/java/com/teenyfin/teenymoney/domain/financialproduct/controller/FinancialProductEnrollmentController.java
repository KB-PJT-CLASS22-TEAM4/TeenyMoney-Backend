package com.teenyfin.teenymoney.domain.financialproduct.controller;

import com.teenyfin.teenymoney.domain.financialproduct.dto.request.DepositEnrollmentRequestDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.request.LoanEnrollmentRequestDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.request.SavingEnrollmentRequestDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.request.SavingPaymentRequestDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.FinancialProductEnrollmentRequestResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.SavingPaymentResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.service.FinancialProductEnrollmentService;
import com.teenyfin.teenymoney.domain.financialproduct.service.FreeSavingPaymentService;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponses;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/financial-products")
@Api(tags = "Financial Product Enrollment", description = "자녀 금융상품 가입 요청")
public class FinancialProductEnrollmentController {
    private final FinancialProductEnrollmentService enrollmentService;
    private final FreeSavingPaymentService freeSavingPaymentService;

    public FinancialProductEnrollmentController(
            FinancialProductEnrollmentService enrollmentService,
            FreeSavingPaymentService freeSavingPaymentService) {
        this.enrollmentService = enrollmentService;
        this.freeSavingPaymentService = freeSavingPaymentService;
    }

    @PostMapping("/deposit-enrollments")
    @ApiOperation(value = "예금 가입 요청")
    public ApiResponse<FinancialProductEnrollmentRequestResponseDTO> requestDeposit(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody DepositEnrollmentRequestDTO request) {
        return ApiResponse.ok(enrollmentService.requestDeposit(principal, request));
    }

    @PostMapping("/saving-enrollments")
    @ApiOperation(value = "적금 가입 요청")
    public ApiResponse<FinancialProductEnrollmentRequestResponseDTO> requestSaving(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody SavingEnrollmentRequestDTO request) {
        return ApiResponse.ok(enrollmentService.requestSaving(principal, request));
    }

    @PostMapping("/loan-enrollments")
    @ApiOperation(value = "대출 가입 요청")
    public ApiResponse<FinancialProductEnrollmentRequestResponseDTO> requestLoan(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody LoanEnrollmentRequestDTO request) {
        return ApiResponse.ok(enrollmentService.requestLoan(principal, request));
    }

    @PostMapping("/saving-enrollments/{enrollmentId}/payments")
    @ApiOperation(value = "자유적금 직접납입",
            notes = "자녀 지갑에서 자유적금 지갑으로 납입하며 UUID 재요청은 중복 출금되지 않습니다.")
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "납입 성공"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "요청 형식 오류, 정기적금 또는 월 한도 초과"),
            @io.swagger.annotations.ApiResponse(code = 403, message = "자녀 회원이 아님"),
            @io.swagger.annotations.ApiResponse(code = 404, message = "본인의 적금 가입 정보를 찾을 수 없음"),
            @io.swagger.annotations.ApiResponse(code = 409, message = "비활성 가입 또는 멱등성 키 충돌")
    })
    public ApiResponse<SavingPaymentResponseDTO> payFreeSaving(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long enrollmentId,
            @Valid @RequestBody SavingPaymentRequestDTO request) {
        return ApiResponse.ok(
                freeSavingPaymentService.pay(principal, enrollmentId, request));
    }
}
