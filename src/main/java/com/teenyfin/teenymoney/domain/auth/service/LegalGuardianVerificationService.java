package com.teenyfin.teenymoney.domain.auth.service;

import com.teenyfin.teenymoney.global.auth.LegalGuardianConsentStore;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
// 보호자 SMS 인증을 완료한 뒤 회원가입에서 사용할 일회용 동의 토큰을 발급한다.
public class LegalGuardianVerificationService {

    private final PhoneVerificationService phoneVerificationService;
    private final LegalGuardianConsentStore store;
    private final Clock clock;

    public LegalGuardianVerificationService(
            PhoneVerificationService phoneVerificationService,
            LegalGuardianConsentStore store,
            Clock clock) {
        this.phoneVerificationService = phoneVerificationService;
        this.store = store;
        this.clock = clock;
    }

    public void sendCode(String phoneNumber) {
        // [보호자 가입 흐름 2] 기존 SMS 인증 서비스를 재사용해 인증번호를 발송한다.
        // ponytail: 회원/보호자 인증은 현재 같은 전화번호 키를 공유한다. 동시 인증이 필요해지면 용도별 키로 분리한다.
        phoneVerificationService.sendCode(phoneNumber);
    }

    public String confirm(
            String name,
            String relationship,
            String phoneNumber,
            String verificationCode,
            String serviceTermsVersion,
            String privacyTermsVersion) {
        // [보호자 가입 흐름 4] 번호를 숫자로 정규화하고 사용자가 입력한 6자리 인증번호를 검증한다.
        String normalizedPhoneNumber = phoneNumber.replaceAll("\\D", "");
        phoneVerificationService.verify(normalizedPhoneNumber, verificationCode);

        // [보호자 가입 흐름 5] 인증 당시의 보호자 정보와 약관 버전을 스냅샷으로 만든다.
        String token = store.save(new LegalGuardianConsent(
                name.trim(),
                normalizedPhoneNumber,
                relationship,
                "SMS_TEST",
                UUID.randomUUID().toString(),
                LocalDateTime.now(clock),
                serviceTermsVersion,
                privacyTermsVersion));

        // [보호자 가입 흐름 7] 재사용을 막기 위해 SMS 인증번호를 삭제하고 발급된 동의 토큰을 반환한다.
        phoneVerificationService.consume(normalizedPhoneNumber);
        return token;
    }
}
