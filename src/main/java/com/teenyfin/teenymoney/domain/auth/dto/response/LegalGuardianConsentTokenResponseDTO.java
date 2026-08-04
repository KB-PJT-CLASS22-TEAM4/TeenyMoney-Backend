package com.teenyfin.teenymoney.domain.auth.dto.response;

// [보호자 가입 흐름 7] 인증 성공 후 반환하며, 만 14세 미만 회원가입 요청에 그대로 사용한다.
public record LegalGuardianConsentTokenResponseDTO(String legalGuardianConsentToken) {
}
