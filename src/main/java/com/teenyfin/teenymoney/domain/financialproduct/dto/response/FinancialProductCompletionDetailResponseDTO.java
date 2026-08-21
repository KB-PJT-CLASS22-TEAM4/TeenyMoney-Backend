package com.teenyfin.teenymoney.domain.financialproduct.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductType;
import io.swagger.annotations.ApiModel;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@ApiModel(description = "완료된 금융상품의 실제 정산 및 처리 상세")
public class FinancialProductCompletionDetailResponseDTO {
    private Long enrollmentId;
    private FinancialProductType productType;
    private String productName;
    private String status;
    private String completionType;
    private Integer termMonths;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate startDate;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate maturityDate;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime completedAt;
    private BigDecimal appliedRate;
    private Long principalAmount;
    private Long interestAmount;
    private Long totalAmount;
    private List<DepositCompletionPeriodResponseDTO> depositPeriods;
    private List<SavingCompletionPaymentResponseDTO> savingPayments;
    private List<LoanCompletionRepaymentResponseDTO> loanRepayments;
}
