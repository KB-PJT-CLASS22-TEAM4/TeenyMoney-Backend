package com.teenyfin.teenymoney.domain.auth.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@Getter
@Setter
@NoArgsConstructor
@ApiModel(description = "휴대폰 인증번호 발송 요청")
public class PhoneVerificationSendRequestDTO {

    @ApiModelProperty(value = "인증번호를 받을 국내 휴대폰 번호", required = true, example = "010-1234-5678")
    @NotBlank(message = "휴대폰 번호는 필수입니다.")
    @Pattern(
            regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$",
            message = "휴대폰 번호 형식이 올바르지 않습니다.")
    private String phoneNumber;
}
