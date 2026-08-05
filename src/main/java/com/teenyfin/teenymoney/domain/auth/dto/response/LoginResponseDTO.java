package com.teenyfin.teenymoney.domain.auth.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;

@Getter
@ApiModel(description = "로그인 성공 응답")
public class LoginResponseDTO {

    @ApiModelProperty(value = "Authorization Bearer 헤더에 사용할 Access Token")
    private final String accessToken;
    @ApiModelProperty(value = "회원 ID", example = "17")
    private final Long memberId;
    @ApiModelProperty(value = "서버가 계산한 회원 역할", allowableValues = "PARENT,CHILD", example = "CHILD")
    private final String role;
    @ApiModelProperty(value = "회원 이름", example = "김자녀")
    private final String name;

    private LoginResponseDTO(
            String accessToken,
            Long memberId,
            String role,
            String name) {
        this.accessToken = accessToken;
        this.memberId = memberId;
        this.role = role;
        this.name = name;
    }

    public static LoginResponseDTO of(
            String accessToken,
            Long memberId,
            String role,
            String name) {
        return new LoginResponseDTO(accessToken, memberId, role, name);
    }
}
