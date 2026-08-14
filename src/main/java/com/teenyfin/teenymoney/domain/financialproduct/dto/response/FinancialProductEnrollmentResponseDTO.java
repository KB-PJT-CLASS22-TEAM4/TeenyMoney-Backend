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
import java.time.LocalDateTime;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel(description = "자녀의 금융상품 가입 계약")
public class FinancialProductEnrollmentResponseDTO {
    @ApiModelProperty(value = "가입 계약 ID", example = "21")
    private final Long enrollmentId;
    private final Long productId;
    private final FinancialProductType productType;
    private final String financialCompanyName;
    private final String productName;
    @ApiModelProperty(value = "적금 유형", example = "FIXED")
    private final String savingsType;
    @ApiModelProperty(value = "이자 계산 방식", example = "SIMPLE")
    private final String interestCalculationType;
    @ApiModelProperty(value = "가입 계약 상태", example = "ACTIVE")
    private final String status;
    @ApiModelProperty(value = "가입 시점 확정금리(%)", example = "4.50")
    private final BigDecimal appliedRate;
    @ApiModelProperty(value = "가입 시점에 저장한 중도해지 기준금리(%)", example = "1.00")
    private final BigDecimal appliedEarlyTerminationRate;
    private final BigDecimal appliedLateFeeRate;
    private final Integer termMonths;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private final LocalDate startDate;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private final LocalDate maturityDate;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private final LocalDateTime closedAt;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private final LocalDateTime requestedAt;
    private final Long depositAmount;
    private final Long monthlyAmount;
    private final Long accumulatedAmount;
    private final Integer paidCount;
    private final Integer totalPaymentCount;
    private final Integer paymentDay;
    private final Boolean autoTransfer;
    private final Long principalAmount;
    private final Long outstandingPrincipal;
    private final Long overdueInterest;
    private final String repaymentType;

    public static FinancialProductEnrollmentResponseDTO of(
            FinancialProductEnrollmentVO enrollment) {
        return FinancialProductEnrollmentResponseDTO.builder()
                .enrollmentId(enrollment.getEnrollmentId())
                .productId(enrollment.getProductId())
                .productType(enrollment.getProductType())
                .financialCompanyName(enrollment.getFinancialCompanyName())
                .productName(enrollment.getProductName())
                .savingsType(enrollment.getSavingsType())
                .interestCalculationType(
                        enrollment.getInterestCalculationType())
                .status(enrollment.getStatus())
                .appliedRate(enrollment.getAppliedRate())
                .appliedEarlyTerminationRate(
                        enrollment.getAppliedEarlyTerminationRate())
                .appliedLateFeeRate(enrollment.getAppliedLateFeeRate())
                .termMonths(enrollment.getTermMonths())
                .startDate(enrollment.getStartDate())
                .maturityDate(enrollment.getMaturityDate())
                .closedAt(enrollment.getClosedAt())
                .requestedAt(enrollment.getRequestedAt())
                .depositAmount(enrollment.getDepositAmount())
                .monthlyAmount(enrollment.getMonthlyAmount())
                .accumulatedAmount(enrollment.getAccumulatedAmount())
                .paidCount(enrollment.getPaidCount())
                .totalPaymentCount(enrollment.getTotalPaymentCount())
                .paymentDay(enrollment.getPaymentDay())
                .autoTransfer(enrollment.getAutoTransfer())
                .principalAmount(enrollment.getPrincipalAmount())
                .outstandingPrincipal(enrollment.getOutstandingPrincipal())
                .overdueInterest(enrollment.getOverdueInterest())
                .repaymentType(enrollment.getRepaymentType())
                .build();
    }
}
