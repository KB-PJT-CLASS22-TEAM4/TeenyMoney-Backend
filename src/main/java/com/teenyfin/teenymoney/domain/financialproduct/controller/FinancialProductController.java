package com.teenyfin.teenymoney.domain.financialproduct.controller;

import com.teenyfin.teenymoney.domain.financialproduct.dto.response.FinancialProductDetailResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.FinancialProductListResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.service.FinancialProductService;
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

import java.util.List;

@RestController
@RequestMapping("/financial-products")
@Api(tags = "Financial Product", description = "금융상품 조회와 티니점수 기반 예상금리")
public class FinancialProductController {

    private final FinancialProductService financialProductService;

    public FinancialProductController(FinancialProductService financialProductService) {
        this.financialProductService = financialProductService;
    }

    @GetMapping
    @ApiOperation(value = "전체 금융상품 목록 조회",
            notes = "예금, 적금, 대출 상품을 모두 반환하고 현재 자녀의 가입 가능 여부를 계산합니다.")
    public ApiResponse<List<FinancialProductListResponseDTO>> getProducts(
            @AuthenticationPrincipal MemberPrincipal principal) {
        return ApiResponse.ok(financialProductService.getProducts(principal));
    }

    @GetMapping("/deposit")
    @ApiOperation(value = "예금 상품 목록 조회")
    public ApiResponse<List<FinancialProductListResponseDTO>> getDepositProducts(
            @AuthenticationPrincipal MemberPrincipal principal) {
        return ApiResponse.ok(financialProductService.getDepositProducts(principal));
    }

    @GetMapping("/saving")
    @ApiOperation(value = "적금 상품 목록 조회")
    public ApiResponse<List<FinancialProductListResponseDTO>> getSavingProducts(
            @AuthenticationPrincipal MemberPrincipal principal) {
        return ApiResponse.ok(financialProductService.getSavingProducts(principal));
    }

    @GetMapping("/loan")
    @ApiOperation(value = "대출 상품 목록 조회")
    public ApiResponse<List<FinancialProductListResponseDTO>> getLoanProducts(
            @AuthenticationPrincipal MemberPrincipal principal) {
        return ApiResponse.ok(financialProductService.getLoanProducts(principal));
    }

    @GetMapping("/deposit/{productId}")
    @ApiOperation(value = "예금 상품 상세 조회")
    public ApiResponse<FinancialProductDetailResponseDTO> getDepositProductDetail(
            @AuthenticationPrincipal MemberPrincipal principal,
            @ApiParam(value = "예금 상품 ID", example = "1")
            @PathVariable Long productId) {
        return ApiResponse.ok(financialProductService.getDepositProductDetail(principal, productId));
    }

    @GetMapping("/saving/{productId}")
    @ApiOperation(value = "적금 상품 상세 조회")
    public ApiResponse<FinancialProductDetailResponseDTO> getSavingProductDetail(
            @AuthenticationPrincipal MemberPrincipal principal,
            @ApiParam(value = "적금 상품 ID", example = "1")
            @PathVariable Long productId) {
        return ApiResponse.ok(financialProductService.getSavingProductDetail(principal, productId));
    }

    @GetMapping("/loan/{productId}")
    @ApiOperation(value = "대출 상품 상세 조회")
    public ApiResponse<FinancialProductDetailResponseDTO> getLoanProductDetail(
            @AuthenticationPrincipal MemberPrincipal principal,
            @ApiParam(value = "대출 상품 ID", example = "1")
            @PathVariable Long productId) {
        return ApiResponse.ok(financialProductService.getLoanProductDetail(principal, productId));
    }

}
