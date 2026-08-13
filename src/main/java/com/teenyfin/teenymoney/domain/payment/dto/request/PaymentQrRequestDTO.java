package com.teenyfin.teenymoney.domain.payment.dto.request;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentQrRequestDTO {

    @ApiModelProperty(value = "주문 정보 ID (UUID)", example = "e800ab29-9537-4fc3-9206-8075c402001b")
    @NotBlank
    @Size(max = 100)
    private String orderId;

    @ApiModelProperty(value = "가맹점 이름", example = "스타 코인노래방")
    @NotBlank
    @Size(max = 255)
    private String merchantName;

    @ApiModelProperty(value = "가맹점 업종 코드 (6자리 숫자)", example = "924903")
    @NotBlank
    @Size(min = 6, max = 6)
    private String merchantCode;

    @ApiModelProperty(value = "결제 금액", example = "2000")
    @NotNull
    @Positive
    private Long amount;

    @ApiModelProperty(value = "QR 코드 만료 일시", example = "2026-08-11T06:22:02.126Z")
    @NotNull
    private LocalDateTime expiredAt;
}
