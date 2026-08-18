package com.teenyfin.teenymoney.domain.financialproduct.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;

@Getter
@NoArgsConstructor
@ApiModel(description = "대출 조기상환 요청")
public class LoanEarlyRepaymentRequestDTO {
    @NotNull
    @Positive
    @ApiModelProperty(value = "조기상환 금액", required = true, example = "50000")
    private Long amount;

    @NotNull
    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
    @ApiModelProperty(value = "중복 출금 방지 UUID", required = true,
            example = "550e8400-e29b-41d4-a716-446655440000")
    private String idempotencyKey;

    public LoanEarlyRepaymentRequestDTO(Long amount, String idempotencyKey) {
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
    }
}
