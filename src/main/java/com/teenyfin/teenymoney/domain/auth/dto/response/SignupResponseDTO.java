package com.teenyfin.teenymoney.domain.auth.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;

@Getter
@ApiModel(description = "회원가입 성공 응답")
public class SignupResponseDTO {

    @ApiModelProperty(value = "생성된 회원 ID", example = "17")
    private final Long memberId;

    private SignupResponseDTO(Long memberId) {
        this.memberId = memberId;
    }

    public static SignupResponseDTO of(Long memberId) {
        return new SignupResponseDTO(memberId);
    }
}
