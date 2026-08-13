package com.teenyfin.teenymoney.domain.financialproduct.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductApprovalVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductType;
import io.swagger.annotations.ApiModel;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel(description = "금융상품 가입 승인 요청")
public class FinancialProductApprovalResponseDTO {
    private final Long enrollmentId;
    private final Long childId;
    private final String childName;
    private final Long productId;
    private final FinancialProductType productType;
    private final String productName;
    private final Long requestedAmount;
    private final Integer termMonths;
    private final Integer paymentDay;
    private final Boolean autoTransfer;
    private final String savingsType;
    private final String interestCalculationType;
    private final String repaymentType;
    private final BigDecimal expectedAppliedRate;
    private final BigDecimal earlyTerminationRate;
    private final BigDecimal lateFeeRate;
    private final String status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private final LocalDateTime requestedAt;

    public static FinancialProductApprovalResponseDTO of(
            FinancialProductApprovalVO approval) {
        return FinancialProductApprovalResponseDTO.builder()
                .enrollmentId(approval.getEnrollmentId())
                .childId(approval.getChildId())
                .childName(approval.getChildName())
                .productId(approval.getProductId())
                .productType(approval.getProductType())
                .productName(approval.getProductName())
                .requestedAmount(approval.getRequestedAmount())
                .termMonths(approval.getTermMonths())
                .paymentDay(approval.getPaymentDay())
                .autoTransfer(approval.getAutoTransfer())
                .savingsType(approval.getSavingsType())
                .interestCalculationType(approval.getInterestCalculationType())
                .repaymentType(approval.getRepaymentType())
                .expectedAppliedRate(approval.getAppliedRate())
                .earlyTerminationRate(approval.getEarlyTerminationRate())
                .lateFeeRate(approval.getLateFeeRate())
                .status(approval.getStatus())
                .requestedAt(approval.getRequestedAt())
                .build();
    }
}
