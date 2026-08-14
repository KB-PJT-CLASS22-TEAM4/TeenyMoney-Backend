package com.teenyfin.teenymoney.domain.financialproduct.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductType;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel(description = "금융상품 상세")
public class FinancialProductDetailResponseDTO {
    private final Long productId;
    private final FinancialProductType productType;
    @ApiModelProperty(value = "상품 출처", example = "PARENT")
    private final String productSource;
    private final String financialCompanyName;
    private final String productName;
    private final String description;
    @ApiModelProperty(value = "이번 달 적용 등급 ID", example = "2")
    private final Long appliedGradeId;
    @ApiModelProperty(value = "이번 달 적용 등급명", example = "스타터")
    private final String appliedGradeName;
    @ApiModelProperty(value = "대출상품 최소 요구등급 ID", example = "3")
    private final Long requiredGradeId;
    @ApiModelProperty(value = "대출상품 최소 요구등급명", example = "플러스")
    private final String requiredGradeName;
    private final boolean eligible;
    @ApiModelProperty(value = "가입 불가 사유. 가입 가능하면 null")
    private final String ineligibleReason;
    private final List<Integer> availableTerms;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final List<ProductRateResponseDTO> rates;
    @ApiModelProperty(value = "대출상품 기본금리(%)")
    private final BigDecimal baseRate;
    @ApiModelProperty(value = "대출상품 예상 적용금리(%)")
    private final BigDecimal expectedAppliedRate;
    @ApiModelProperty(value = "진행률별 중도해지 정책을 적용하기 전 기준금리(%)")
    private final BigDecimal earlyTerminationRate;
    private final BigDecimal lateFeeRate;
    private final Long minimumAmount;
    private final Long maximumAmount;
    @ApiModelProperty(value = "적금 적립 유형(FIXED, FREE)")
    private final String savingsType;
    @ApiModelProperty(value = "예·적금 이자 계산 방식(SIMPLE, COMPOUND)")
    private final String interestCalculationType;
    @ApiModelProperty(value = "대출 상환 방식")
    private final String repaymentType;
}
