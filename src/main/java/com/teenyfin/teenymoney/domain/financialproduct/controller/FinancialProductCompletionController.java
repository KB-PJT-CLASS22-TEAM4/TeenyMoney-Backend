package com.teenyfin.teenymoney.domain.financialproduct.controller;

import com.teenyfin.teenymoney.domain.financialproduct.dto.response.FinancialProductCompletionDetailResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.service.FinancialProductCompletionService;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/financial-products")
@Api(tags = "Financial Product Completion", description = "완료 금융상품 실제 이력 조회")
public class FinancialProductCompletionController {
    private final FinancialProductCompletionService completionService;

    public FinancialProductCompletionController(
            FinancialProductCompletionService completionService) {
        this.completionService = completionService;
    }

    @GetMapping("/me/enrollments/{productType}/{enrollmentId}/completion-detail")
    @ApiOperation(value = "자녀 본인의 완료 금융상품 상세 조회",
            notes = "만기된 예금·적금 또는 완납된 대출의 실제 정산 합계와 회차별 이력을 반환합니다.")
    public ApiResponse<FinancialProductCompletionDetailResponseDTO> getMyCompletionDetail(
            @AuthenticationPrincipal MemberPrincipal principal,
            @ApiParam(value = "deposit, saving, loan") @PathVariable String productType,
            @PathVariable Long enrollmentId) {
        return ApiResponse.ok(completionService.getMyCompletionDetail(
                principal, productType, enrollmentId));
    }

    @GetMapping("/children/{childId}/{productType}/{enrollmentId}/completion-detail")
    @ApiOperation(value = "부모의 자녀 완료 금융상품 상세 조회",
            notes = "연결된 자녀의 만기 예금·적금 및 완납 대출 실제 이력만 조회할 수 있습니다.")
    public ApiResponse<FinancialProductCompletionDetailResponseDTO> getChildCompletionDetail(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long childId,
            @ApiParam(value = "deposit, saving, loan") @PathVariable String productType,
            @PathVariable Long enrollmentId) {
        return ApiResponse.ok(completionService.getChildCompletionDetail(
                principal, childId, productType, enrollmentId));
    }
}
