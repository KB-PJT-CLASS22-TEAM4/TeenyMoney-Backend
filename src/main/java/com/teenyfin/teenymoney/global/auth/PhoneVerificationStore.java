package com.teenyfin.teenymoney.global.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class PhoneVerificationStore {

    private static final String CODE_PREFIX = "phone-verification:code:";
    private static final String COOLDOWN_PREFIX = "phone-verification:cooldown:";
    private static final String ATTEMPTS_PREFIX = "phone-verification:attempts:";

    private final StringRedisTemplate redisTemplate;
    private final Duration verificationTtl;
    private final Duration cooldownTtl;

    public PhoneVerificationStore(
            StringRedisTemplate redisTemplate,
            @Value("${sms.verification-ttl-seconds}") long verificationTtlSeconds,
            @Value("${sms.resend-cooldown-seconds}") long cooldownTtlSeconds) {
        this.redisTemplate = redisTemplate;
        this.verificationTtl = Duration.ofSeconds(verificationTtlSeconds);
        this.cooldownTtl = Duration.ofSeconds(cooldownTtlSeconds);
    }

    public void saveCode(String phoneNumber, String code) {
        redisTemplate.opsForValue().set(codeKey(phoneNumber), code, verificationTtl);
    }

    public String findCode(String phoneNumber) {
        return redisTemplate.opsForValue().get(codeKey(phoneNumber));
    }

    public boolean isCooldownActive(String phoneNumber) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey(phoneNumber)));
    }

    public void startCooldown(String phoneNumber) {
        redisTemplate.opsForValue().set(cooldownKey(phoneNumber), "1", cooldownTtl);
    }

    public long getAttempts(String phoneNumber) {
        String attempts = redisTemplate.opsForValue().get(attemptsKey(phoneNumber));
        return attempts == null ? 0L : Long.parseLong(attempts);
    }

    public Long incrementAttempts(String phoneNumber) {
        String key = attemptsKey(phoneNumber);
        Long attempts = redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, verificationTtl);
        return attempts;
    }

    public void resetAttempts(String phoneNumber) {
        redisTemplate.delete(attemptsKey(phoneNumber));
    }

    public void deleteCode(String phoneNumber) {
        redisTemplate.delete(codeKey(phoneNumber));
    }

    public void deleteAll(String phoneNumber) {
        redisTemplate.delete(List.of(
                codeKey(phoneNumber),
                cooldownKey(phoneNumber),
                attemptsKey(phoneNumber)));
    }

    private String codeKey(String phoneNumber) {
        return CODE_PREFIX + phoneNumber;
    }

    private String cooldownKey(String phoneNumber) {
        return COOLDOWN_PREFIX + phoneNumber;
    }

    private String attemptsKey(String phoneNumber) {
        return ATTEMPTS_PREFIX + phoneNumber;
    }
}
