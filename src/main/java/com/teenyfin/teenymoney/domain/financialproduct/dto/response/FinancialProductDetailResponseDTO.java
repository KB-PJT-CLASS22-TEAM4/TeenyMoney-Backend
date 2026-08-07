package com.teenyfin.teenymoney.domain.financialproduct.dto.response;

import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductType;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
@ApiModel(description = "금융상품 상세")
public class FinancialProductDetailResponseDTO {
    private final Long productId;
    private final FinancialProductType productType;
    private final String financialCompanyName;
    private final String productName;
    private final String description;
    private final Integer currentTeenyScore;
    private final String gradeName;
    private final Integer minimumTeenyScore;
    private final boolean eligible;
    @ApiModelProperty(value = "가입 불가 사유. 가입 가능하면 null")
    private final String ineligibleReason;
    private final List<Integer> availableTerms;
    private final List<ProductRateResponseDTO> rates;
    private final BigDecimal expectedAppliedRate;
    private final BigDecimal earlyTerminationRate;
    private final BigDecimal lateFeeRate;
    private final Long minimumAmount;
    private final Long maximumAmount;
    private final String savingsType;
    private final String interestCalculationType;
    private final String repaymentType;
}
