package com.teenyfin.teenymoney.domain.payment.dto.request;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequestDTO {

    @ApiModelProperty(value = "주문 정보 ID (UUID)", example = "e800ab29-9537-4fc3-9206-8075c402001b")
    @NotBlank
    @Size(max = 100)
    private String orderId;

    @ApiModelProperty(value = "중복 결제를 방지하기 위한 멱등성 키 (UUID)", example = "0aee98b7-c60f-4d4e-b5b1-fd15bf944a1c")
    @NotBlank
    @Size(max = 36)
    private String idempotencyKey;

    @ApiModelProperty(value = "결제 비밀번호 (숫자 6자리)", example = "123456")
    @NotBlank
    @Pattern(regexp = "^\\d{6}$", message = "결제 비밀번호는 숫자 6자리여야 합니다.")
    private String password;
}
