package com.teenyfin.teenymoney.domain.charge.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotBlank;


// 프론트가 카드 등록 폼에서 우리 서버로 보낼 요청 body 모양.
// TossCardBillingKeyRequestDTO(토스한테 보낼 모양)랑 필드가 거의 겹치지만 다른 클래스인 이유:
// 이 DTO는 "우리 API 계약"이고, 그건 "토스 API 계약"이라 서로 독립적으로 바뀔 수 있어야 함
// (예: 나중에 우리 쪽만 필드를 하나 더 받고 싶어질 수도 있음). customerKey가 여기 없는 이유는
// 로그인한 부모 걸 서버가 알아서 채우기 때문 - 클라이언트가 몰라도 됨.
@Getter
@Setter
@NoArgsConstructor
@ApiModel(description = "카드 결제수단 등록 요청")
public class CardRegisterRequestDTO {

    @ApiModelProperty(value = "카드번호", required = true, example = "4330123412341234")
    @NotBlank(message = "카드번호는 필수입니다.")
    private String cardNumber;

    @ApiModelProperty(value = "카드 유효기간 연도(YY)", required = true, example = "27")
    @NotBlank(message = "카드 유효기간 연도는 필수입니다.")
    private String cardExpirationYear;

    @ApiModelProperty(value = "카드 유효기간 월(MM)", required = true, example = "05")
    @NotBlank(message = "카드 유효기간 월은 필수입니다.")
    private String cardExpirationMonth;

    @ApiModelProperty(value = "생년월일 6자리(YYMMDD) 또는 사업자등록번호 10자리", required = true, example = "990101")
    @NotBlank(message = "생년월일 또는 사업자등록번호는 필수입니다.")
    private String customerIdentityNumber;

    @ApiModelProperty(value = "카드 비밀번호 앞 2자리", required = true, example = "12")
    @NotBlank(message = "카드 비밀번호 앞 2자리는 필수입니다.")
    private String cardPassword;

}
