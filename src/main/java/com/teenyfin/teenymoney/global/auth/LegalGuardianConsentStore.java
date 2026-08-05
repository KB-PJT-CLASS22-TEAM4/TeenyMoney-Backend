package com.teenyfin.teenymoney.global.auth;

import com.teenyfin.teenymoney.domain.auth.service.LegalGuardianConsent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
// 보호자 인증 결과를 회원가입 완료 전까지만 Redis에 임시 보관한다.
public class LegalGuardianConsentStore {

    private static final String PREFIX = "legal-guardian-consent:";
    private static final String SEPARATOR = "\u001F";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public LegalGuardianConsentStore(
            StringRedisTemplate redisTemplate,
            @Value("${legal-guardian.consent-token-ttl-seconds}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public String save(LegalGuardianConsent consent) {
        // [보호자 가입 흐름 6] 추측하기 어려운 UUID 토큰으로 저장하고 설정된 TTL(기본 10분)을 적용한다.
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(key(token), encode(consent), ttl);
        return token;
    }

    public LegalGuardianConsent find(String token) {
        // [보호자 가입 흐름 10] 가입 요청의 토큰으로 인증 스냅샷을 조회한다. 만료·손상된 값은 무효로 처리한다.
        if (token == null || token.isBlank()) {
            return null;
        }
        String value = redisTemplate.opsForValue().get(key(token));
        if (value == null) {
            return null;
        }
        String[] fields = value.split(SEPARATOR, -1);
        if (fields.length != 8) {
            return null;
        }
        return new LegalGuardianConsent(
                fields[0], fields[1], fields[2], fields[3], fields[4],
                LocalDateTime.parse(fields[5]), fields[6], fields[7]);
    }

    public void delete(String token) {
        // [보호자 가입 흐름 15] 가입 성공 후 일회용 토큰을 삭제해 재가입에 사용할 수 없게 한다.
        redisTemplate.delete(key(token));
    }

    private String encode(LegalGuardianConsent consent) {
        // Redis에는 단일 문자열로 저장하기 위해 충돌 가능성이 낮은 제어문자로 필드를 구분한다.
        return String.join(SEPARATOR,
                consent.name(),
                consent.phoneNumber(),
                consent.relationship(),
                consent.verificationMethod(),
                consent.verificationReference(),
                consent.verifiedAt().toString(),
                consent.serviceTermsVersion(),
                consent.privacyTermsVersion());
    }

    private String key(String token) {
        // 다른 Redis 데이터와 키 공간이 겹치지 않도록 전용 접두사를 붙인다.
        return PREFIX + token;
    }
}
