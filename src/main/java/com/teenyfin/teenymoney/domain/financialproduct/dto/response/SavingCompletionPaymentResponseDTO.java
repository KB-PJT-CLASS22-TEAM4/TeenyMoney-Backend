package com.teenyfin.teenymoney.domain.financialproduct.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@ApiModel(description = "완료 적금의 회차별 실제 납입 내역")
public class SavingCompletionPaymentResponseDTO {
    private int installmentNo;
    private long scheduledAmount;
    private long paidAmount;
    private String status;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime paidAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime processedAt;
    private long interestAmount;
}
