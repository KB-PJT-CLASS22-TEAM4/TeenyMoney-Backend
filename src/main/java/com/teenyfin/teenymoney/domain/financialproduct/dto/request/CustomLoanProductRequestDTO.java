package com.teenyfin.teenymoney.domain.financialproduct.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "부모의 자녀 전용 대출상품 생성 요청")
public class CustomLoanProductRequestDTO {
    @NotBlank @Size(max = 100) private String productName;
    @Size(max = 1000) private String description;
    @NotBlank private String repaymentType;

    @NotEmpty
    @Size(max = 4)
    @ApiModelProperty(value = "가입 가능한 기간 목록(1, 3, 6, 12개월)", example = "[1, 3, 6, 12]")
    private List<@NotNull Integer> availableTerms;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = false)
    @ApiModelProperty(value = "부모가 설정하는 상품 기본금리", example = "5.00")
    private BigDecimal interestRate;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = false)
    private BigDecimal lateFeeRate;
    @NotNull @Positive private Long minimumAmount;
    @NotNull @Positive private Long maximumAmount;
    @NotNull @Positive private Long requiredGradeId;
}
