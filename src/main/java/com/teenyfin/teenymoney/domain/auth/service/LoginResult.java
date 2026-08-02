package com.teenyfin.teenymoney.domain.auth.service;

import com.teenyfin.teenymoney.domain.auth.dto.response.LoginResponseDTO;

public record LoginResult(
        String accessToken,
        String refreshToken,
        Long memberId,
        String role,
        String name) {

    public LoginResponseDTO toResponse() {
        return LoginResponseDTO.of(accessToken, memberId, role, name);
    }
}
