package com.teenyfin.teenymoney.domain.financialproduct.controller;

import com.teenyfin.teenymoney.domain.financialproduct.dto.response.FinancialProductApprovalResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.service.FinancialProductApprovalService;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/financial-products/approval-requests")
@Api(tags = "Financial Product Approval", description = "부모 금융상품 가입 승인")
public class FinancialProductApprovalController {
    private final FinancialProductApprovalService approvalService;

    public FinancialProductApprovalController(
            FinancialProductApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping
    @ApiOperation(value = "가입 승인 요청 목록 조회")
    public ApiResponse<List<FinancialProductApprovalResponseDTO>> getApprovals(
            @AuthenticationPrincipal MemberPrincipal principal) {
        return ApiResponse.ok(approvalService.getPendingApprovals(principal));
    }

    @GetMapping("/{productType}/{enrollmentId}")
    @ApiOperation(value = "가입 승인 요청 상세 조회")
    public ApiResponse<FinancialProductApprovalResponseDTO> getApproval(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable String productType,
            @PathVariable Long enrollmentId) {
        return ApiResponse.ok(approvalService.getPendingApproval(
                principal, productType, enrollmentId));
    }

    @PostMapping("/{productType}/{enrollmentId}/approve")
    @ApiOperation(value = "가입 요청 승인")
    public ApiResponse<Void> approve(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable String productType,
            @PathVariable Long enrollmentId) {
        approvalService.approve(principal, productType, enrollmentId);
        return ApiResponse.ok();
    }

    @PostMapping("/{productType}/{enrollmentId}/reject")
    @ApiOperation(value = "가입 요청 거절")
    public ApiResponse<Void> reject(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable String productType,
            @PathVariable Long enrollmentId) {
        approvalService.reject(principal, productType, enrollmentId);
        return ApiResponse.ok();
    }
}
