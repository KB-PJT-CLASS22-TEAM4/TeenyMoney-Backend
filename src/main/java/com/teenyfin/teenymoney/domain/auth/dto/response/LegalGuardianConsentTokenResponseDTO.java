package com.teenyfin.teenymoney.domain.auth.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

// [보호자 가입 흐름 7] 인증 성공 후 반환하며, 만 14세 미만 회원가입 요청에 그대로 사용한다.
@ApiModel(description = "법정대리인 인증 완료 응답")
public record LegalGuardianConsentTokenResponseDTO(
        @ApiModelProperty(
                value = "회원가입에서 한 번만 사용할 수 있는 법정대리인 동의 토큰",
                example = "ea6b50b8-e517-4b5d-bd10-3c175db8463e")
        String legalGuardianConsentToken) {
}
