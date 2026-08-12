package com.teenyfin.teenymoney.domain.financialproduct.controller;

import com.teenyfin.teenymoney.domain.financialproduct.dto.request.DepositEnrollmentRequestDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.request.LoanEnrollmentRequestDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.request.SavingEnrollmentRequestDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.FinancialProductEnrollmentRequestResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.service.FinancialProductEnrollmentService;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/financial-products")
@Api(tags = "Financial Product Enrollment", description = "자녀 금융상품 가입 요청")
public class FinancialProductEnrollmentController {
    private final FinancialProductEnrollmentService enrollmentService;

    public FinancialProductEnrollmentController(
            FinancialProductEnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
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
}
