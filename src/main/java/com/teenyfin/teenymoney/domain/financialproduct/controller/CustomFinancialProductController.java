package com.teenyfin.teenymoney.domain.financialproduct.controller;

import com.teenyfin.teenymoney.domain.financialproduct.dto.request.CustomDepositProductRequestDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.request.CustomLoanProductRequestDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.request.CustomSavingProductRequestDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.CustomFinancialProductResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.service.CustomFinancialProductService;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/** 부모가 본인과 연결된 특정 자녀만 이용할 수 있는 금융상품을 생성한다. */
@RestController
@RequestMapping("/financial-products/children/{childId}")
@Api(tags = "Parent Custom Financial Product", description = "부모의 자녀 전용 금융상품 생성")
public class CustomFinancialProductController {
    private final CustomFinancialProductService customFinancialProductService;

    public CustomFinancialProductController(
            CustomFinancialProductService customFinancialProductService) {
        this.customFinancialProductService = customFinancialProductService;
    }

    @PostMapping("/custom-deposits")
    @ApiOperation(value = "자녀 전용 예금상품 생성")
    public ApiResponse<CustomFinancialProductResponseDTO> createDeposit(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long childId,
            @Valid @RequestBody CustomDepositProductRequestDTO request) {
        return ApiResponse.ok(customFinancialProductService
                .createDeposit(principal, childId, request));
    }

    @PostMapping("/custom-savings")
    @ApiOperation(value = "자녀 전용 적금상품 생성")
    public ApiResponse<CustomFinancialProductResponseDTO> createSaving(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long childId,
            @Valid @RequestBody CustomSavingProductRequestDTO request) {
        return ApiResponse.ok(customFinancialProductService
                .createSaving(principal, childId, request));
    }

    @PostMapping("/custom-loans")
    @ApiOperation(value = "자녀 전용 대출상품 생성")
    public ApiResponse<CustomFinancialProductResponseDTO> createLoan(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long childId,
            @Valid @RequestBody CustomLoanProductRequestDTO request) {
        return ApiResponse.ok(customFinancialProductService
                .createLoan(principal, childId, request));
    }

    @DeleteMapping("/custom-deposits/{productId}")
    @ApiOperation(value = "자녀 전용 예금상품 삭제",
            notes = "본인이 만든 상품만 삭제할 수 있고, 승인 대기 또는 가입 중인 신청이 있으면 거절됩니다.")
    public ApiResponse<Void> deleteDeposit(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long childId,
            @PathVariable Long productId) {
        customFinancialProductService.deleteDeposit(principal, childId, productId);
        return ApiResponse.ok();
    }

    @DeleteMapping("/custom-savings/{productId}")
    @ApiOperation(value = "자녀 전용 적금상품 삭제",
            notes = "본인이 만든 상품만 삭제할 수 있고, 승인 대기 또는 가입 중인 신청이 있으면 거절됩니다.")
    public ApiResponse<Void> deleteSaving(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long childId,
            @PathVariable Long productId) {
        customFinancialProductService.deleteSaving(principal, childId, productId);
        return ApiResponse.ok();
    }

    @DeleteMapping("/custom-loans/{productId}")
    @ApiOperation(value = "자녀 전용 대출상품 삭제",
            notes = "본인이 만든 상품만 삭제할 수 있고, 승인 대기 또는 가입 중인 신청이 있으면 거절됩니다.")
    public ApiResponse<Void> deleteLoan(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long childId,
            @PathVariable Long productId) {
        customFinancialProductService.deleteLoan(principal, childId, productId);
        return ApiResponse.ok();
    }
}
