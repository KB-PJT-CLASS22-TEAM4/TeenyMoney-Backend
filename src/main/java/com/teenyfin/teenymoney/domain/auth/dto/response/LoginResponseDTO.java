package com.teenyfin.teenymoney.domain.auth.dto.response;

import lombok.Getter;

@Getter
public class LoginResponseDTO {

    private final String accessToken;
    private final Long memberId;
    private final String role;
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
