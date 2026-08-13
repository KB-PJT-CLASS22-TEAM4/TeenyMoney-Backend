package com.teenyfin.teenymoney.domain.financialproduct.dto.request;

import io.swagger.annotations.ApiModel;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "적금 가입 요청")
public class SavingEnrollmentRequestDTO {
    @NotNull
    @Positive
    private Long productId;
    @NotNull
    @Positive
    private Long monthlyAmount;
    @NotNull
    private Integer termMonths;
    @NotNull
    @Min(1)
    @Max(28)
    private Integer paymentDay;
    @NotNull
    private Boolean autoTransfer;
}
