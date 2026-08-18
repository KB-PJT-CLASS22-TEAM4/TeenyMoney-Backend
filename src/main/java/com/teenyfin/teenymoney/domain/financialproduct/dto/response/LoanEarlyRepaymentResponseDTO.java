package com.teenyfin.teenymoney.domain.financialproduct.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;

@Getter
@ApiModel(description = "대출 조기상환 예상 또는 실행 결과")
public class LoanEarlyRepaymentResponseDTO {
    @ApiModelProperty(value = "가입 계약 아이디", example = "3")
    private final Long enrollmentId;
    @ApiModelProperty(value = "요청 금액", example = "50000")
    private final long requestedAmount;
    @ApiModelProperty(value = "상환 전 대출 원금", example = "75000")
    private final long currentOutstandingPrincipal;
    @ApiModelProperty(value = "상환 전 연체이자", example = "3000")
    private final long currentOverdueInterest;
    @ApiModelProperty(value = "연체이자에 충당된 금액", example = "1200")
    private final long paidInterestAmount;
    @ApiModelProperty(value = "원금에 충당된 금액", example = "48800")
    private final long paidPrincipalAmount;
    @ApiModelProperty(value = "남은 대출 원금", example = "0")
    private final long remainingOutstandingPrincipal;
    @ApiModelProperty(value = "남은 연체이자", example = "0")
    private final long remainingOverdueInterest;
    @ApiModelProperty(value = "처리 후 대출 상태", example = "REPAID")
    private final String status;
    @ApiModelProperty(value = "티니점수 변동량", example = "6")
    private final int scoreChange;
    @ApiModelProperty(value = "실제 조기상환 실행 여부")
    private final boolean executed;

    public LoanEarlyRepaymentResponseDTO(
            Long enrollmentId, long requestedAmount,
            long currentOutstandingPrincipal, long currentOverdueInterest,
            long paidInterestAmount, long paidPrincipalAmount,
            long remainingOutstandingPrincipal, long remainingOverdueInterest,
            String status, int scoreChange, boolean executed) {
        this.enrollmentId = enrollmentId;
        this.requestedAmount = requestedAmount;
        this.currentOutstandingPrincipal = currentOutstandingPrincipal;
        this.currentOverdueInterest = currentOverdueInterest;
        this.paidInterestAmount = paidInterestAmount;
        this.paidPrincipalAmount = paidPrincipalAmount;
        this.remainingOutstandingPrincipal = remainingOutstandingPrincipal;
        this.remainingOverdueInterest = remainingOverdueInterest;
        this.status = status;
        this.scoreChange = scoreChange;
        this.executed = executed;
    }
}
