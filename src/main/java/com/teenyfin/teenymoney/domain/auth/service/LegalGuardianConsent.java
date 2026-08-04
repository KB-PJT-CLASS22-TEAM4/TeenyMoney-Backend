package com.teenyfin.teenymoney.domain.auth.service;

import java.time.LocalDateTime;

// [보호자 가입 흐름 5] SMS 인증 시점의 보호자 정보와 동의 약관을 변경 불가능한 값으로 묶는다.
public record LegalGuardianConsent(
        String name,
        String phoneNumber,
        String relationship,
        String verificationMethod,
        String verificationReference,
        LocalDateTime verifiedAt,
        String serviceTermsVersion,
        String privacyTermsVersion) {
}
