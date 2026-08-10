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
@ApiModel(description = "금융상품 목록 항목")
public class FinancialProductListResponseDTO {
    private final Long productId;
    private final FinancialProductType productType;
    private final String financialCompanyName;
    private final String productName;
    private final Integer minimumTeenyScore;
    private final Integer currentTeenyScore;
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
    @ApiModelProperty(value = "대출 연체금리(%)")
    private final BigDecimal lateFeeRate;
    @ApiModelProperty(value = "적금 적립 유형(FIXED, FREE)")
    private final String savingsType;
    @ApiModelProperty(value = "예·적금 이자 계산 방식(SIMPLE, COMPOUND)")
    private final String interestCalculationType;
    @ApiModelProperty(value = "대출 상환 방식")
    private final String repaymentType;
}
