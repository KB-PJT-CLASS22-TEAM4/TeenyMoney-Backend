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
    private final List<ProductRateResponseDTO> rates;
    @ApiModelProperty(value = "대출 등급별 예상 적용금리(%). 예·적금은 null")
    private final BigDecimal expectedAppliedRate;
}
