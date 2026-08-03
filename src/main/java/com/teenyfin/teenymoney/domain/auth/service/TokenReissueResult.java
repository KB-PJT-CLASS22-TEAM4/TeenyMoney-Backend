package com.teenyfin.teenymoney.domain.auth.service;

public record TokenReissueResult(String accessToken, String refreshToken) {
}
