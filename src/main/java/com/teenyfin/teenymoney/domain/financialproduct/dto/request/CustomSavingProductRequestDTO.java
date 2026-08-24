package com.teenyfin.teenymoney.domain.financialproduct.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/** 부모가 자녀 전용 적금의 조건과 기간별 금리를 입력한다. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "부모의 자녀 전용 적금상품 생성 요청")
public class CustomSavingProductRequestDTO {
    @NotBlank @Size(max = 100) private String productName;
    @Size(max = 1000) private String description;
    @NotBlank private String savingsType;
    @NotBlank private String interestCalculationType;
    @NotEmpty @Size(max = 4) @Valid
    private List<CustomProductRateRequestDTO> rates;
    @NotNull @DecimalMin(value = "0.00", inclusive = false)
    @ApiModelProperty(value = "진행률별 중도해지 정책을 적용하기 전 부모 설정 기준금리(%)",
            example = "1.00")
    private BigDecimal earlyTerminationRate;
    @NotNull @Positive private Long minimumMonthlyAmount;
    @NotNull @Positive private Long maximumMonthlyAmount;
    @NotNull @Positive
    @ApiModelProperty(value = "가입에 필요한 최소 월간 적용 등급 ID", example = "2")
    private Long requiredGradeId;
}
