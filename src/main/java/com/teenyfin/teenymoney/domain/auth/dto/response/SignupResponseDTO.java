package com.teenyfin.teenymoney.domain.auth.dto.response;

import lombok.Getter;

@Getter
public class SignupResponseDTO {

    private final Long memberId;

    private SignupResponseDTO(Long memberId) {
        this.memberId = memberId;
    }

    public static SignupResponseDTO of(Long memberId) {
        return new SignupResponseDTO(memberId);
    }
}
