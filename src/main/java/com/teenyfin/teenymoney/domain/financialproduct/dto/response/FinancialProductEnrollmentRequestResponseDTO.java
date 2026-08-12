package com.teenyfin.teenymoney.domain.financialproduct.dto.response;

import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductType;
import io.swagger.annotations.ApiModel;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@ApiModel(description = "금융상품 가입 요청 결과")
public class FinancialProductEnrollmentRequestResponseDTO {
    private final Long enrollmentId;
    private final FinancialProductType productType;
    private final String status;
    private final BigDecimal expectedAppliedRate;
}
