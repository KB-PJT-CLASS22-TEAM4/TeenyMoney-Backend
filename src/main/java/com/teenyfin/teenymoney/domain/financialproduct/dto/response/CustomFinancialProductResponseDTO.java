package com.teenyfin.teenymoney.domain.financialproduct.dto.response;

import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductType;
import io.swagger.annotations.ApiModel;
import lombok.Builder;
import lombok.Getter;

@Getter @Builder
@ApiModel(description = "부모 생성 금융상품 응답")
/** 생성된 부모 상품의 식별정보와 대상 자녀를 반환한다. */
/** 생성된 부모 상품의 식별정보와 대상 자녀를 반환한다. */
public class CustomFinancialProductResponseDTO {
    private final Long productId;
    private final FinancialProductType productType;
    private final String productSource;
    private final String productName;
    private final Long targetChildId;
}
