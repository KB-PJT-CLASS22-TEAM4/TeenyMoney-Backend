package com.teenyfin.teenymoney.domain.financialproduct.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductEnrollmentVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductType;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel(description = "금융상품 가입계약 목록 항목")
public class FinancialProductEnrollmentListResponseDTO {
    @ApiModelProperty(value = "가입계약 ID", example = "21")
    private final Long enrollmentId;
    private final Long productId;
    private final FinancialProductType productType;
    private final String financialCompanyName;
    private final String productName;
    private final String description;
    @ApiModelProperty(value = "적금 유형", example = "FIXED")
    private final String savingsType;
    @ApiModelProperty(value = "이자 계산 방식", example = "SIMPLE")
    private final String interestCalculationType;
    private final String status;
    private final BigDecimal appliedRate;
    @ApiModelProperty(value = "현재 계약 금액", example = "90000")
    private final Long currentAmount;
    private final Long monthlyAmount;
    private final Integer termMonths;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private final LocalDate startDate;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private final LocalDate maturityDate;
    private final Integer paidCount;
    private final Integer totalPaymentCount;

    public static FinancialProductEnrollmentListResponseDTO of(
            FinancialProductEnrollmentVO enrollment) {
        return FinancialProductEnrollmentListResponseDTO.builder()
                .enrollmentId(enrollment.getEnrollmentId())
                .productId(enrollment.getProductId())
                .productType(enrollment.getProductType())
                .financialCompanyName(enrollment.getFinancialCompanyName())
                .productName(enrollment.getProductName())
                .description(enrollment.getDescription())
                .savingsType(enrollment.getSavingsType())
                .interestCalculationType(
                        enrollment.getInterestCalculationType())
                .status(enrollment.getStatus())
                .appliedRate(enrollment.getAppliedRate())
                .currentAmount(currentAmount(enrollment))
                .monthlyAmount(enrollment.getMonthlyAmount())
                .termMonths(enrollment.getTermMonths())
                .startDate(enrollment.getStartDate())
                .maturityDate(enrollment.getMaturityDate())
                .paidCount(enrollment.getPaidCount())
                .totalPaymentCount(enrollment.getTotalPaymentCount())
                .build();
    }

    private static Long currentAmount(FinancialProductEnrollmentVO enrollment) {
        if (enrollment.getProductType() == null) {
            return null;
        }
        return switch (enrollment.getProductType()) {
            case DEPOSIT -> enrollment.getDepositAmount();
            case SAVING -> enrollment.getAccumulatedAmount();
            case LOAN -> enrollment.getOutstandingPrincipal();
        };
    }
}
