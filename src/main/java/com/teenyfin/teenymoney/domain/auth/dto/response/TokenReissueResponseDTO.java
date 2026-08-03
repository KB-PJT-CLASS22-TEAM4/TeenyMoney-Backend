package com.teenyfin.teenymoney.domain.auth.dto.response;

public record TokenReissueResponseDTO(String accessToken) {

    public static TokenReissueResponseDTO of(String accessToken) {
        return new TokenReissueResponseDTO(accessToken);
    }
}
