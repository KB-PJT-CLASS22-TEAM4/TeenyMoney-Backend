package com.teenyfin.teenymoney.domain.paymentPassword.dto.request;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentPasswordRequestDTO {

    @ApiModelProperty(value = "결제 비밀번호 (숫자 6자리)", example = "123456")
    @NotBlank
    @Pattern(regexp = "^\\d{6}$", message = "결제 비밀번호는 숫자 6자리여야 합니다.")
    private String password;
}
