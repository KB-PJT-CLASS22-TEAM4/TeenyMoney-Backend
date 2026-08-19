package com.teenyfin.teenymoney.domain.charge.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;


// 프론트가 "충전하기" 버튼 누를 때 보내는 요청 body 모양.
// walletId는 안 받음 - 로그인한 부모 자신의 지갑으로 고정이라 서버가 알아서 찾음

@Getter
@Setter
@NoArgsConstructor
@ApiModel(description = "카드 충전 요청")
public class ChargeRequestDTO {

    @ApiModelProperty(value = "충전에 사용할 결제수단 아이디", required = true, example = "1")
    @NotNull(message = "결제수단 아이디는 필수입니다.")
    private Long paymentMethodId;

    @ApiModelProperty(value = "충전 금액 (원)", required = true, example = "10000")
    @NotNull(message = "충전 금액은 필수입니다.")
    @Positive(message = "충전 금액은 0보다 커야 합니다.")
    private Long amount;

    // 프론트가 "충전하기"를 누를 때마다 새로 UUID를 만들어서 보내야 함. 만약 응답을
    // 못 받아서 같은 요청을 재시도할 땐, 새로 만들지 말고 아까 그 값을 그대로 다시 보내야
    // 중복 충전을 막을 수 있음 (ChargeService의 멱등키 처리 설계 참고).
    @ApiModelProperty(value = "멱등성 키 (UUID). 재시도 시에도 항상 같은 값을 보내야 함", required = true)
    @NotBlank(message = "멱등성 키는 필수입니다.")
    private String idempotencyKey;

    // PaymentRequestDTO.password와 동일한 형식(숫자 6자리) - 프론트가 결제 비밀번호
    // 입력창에서 받는 값을 그대로 태워보낸다. 재시도(같은 idempotencyKey)일 때도
    // 필드 자체는 항상 채워서 보내야 함 - 검증을 스킵할지 말지는 서버가 판단한다.
    @ApiModelProperty(value = "결제 비밀번호 (숫자 6자리)", required = true, example = "123456")
    @NotBlank(message = "결제 비밀번호는 필수입니다.")
    @Pattern(regexp = "^\\d{6}$", message = "결제 비밀번호는 숫자 6자리여야 합니다.")
    private String password;
}
