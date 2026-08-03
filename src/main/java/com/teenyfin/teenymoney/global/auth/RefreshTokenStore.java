package com.teenyfin.teenymoney.global.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
/*
* Refresh Token을 Redis에 저장, 조회, 삭제 할 수 있습니다.
*
*
*/

@Component
public class RefreshTokenStore {

    // redis에 refresh:값 구조로 저장하기 때문에 접두사로 지정
    private static final String KEY_PREFIX = "refresh:";
    private static final String GENERATION_KEY_PREFIX = "auth:generation:";
    private static final DefaultRedisScript<String> GET_OR_CREATE_GENERATION_SCRIPT =
            new DefaultRedisScript<>("""
                    local value = redis.call('GET', KEYS[1])
                    if not value then
                        value = ARGV[1]
                        redis.call('SET', KEYS[1], value, 'PX', ARGV[2])
                    else
                        redis.call('PEXPIRE', KEYS[1], ARGV[2])
                    end
                    return value
                    """, String.class);
    private static final DefaultRedisScript<Long> ROTATE_SCRIPT =
            new DefaultRedisScript<>("""
                    if redis.call('GET', KEYS[1]) ~= ARGV[1] then
                        return 0
                    end
                    if redis.call('GET', KEYS[2]) ~= ARGV[3] then
                        return 0
                    end
                    redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[4])
                    redis.call('PEXPIRE', KEYS[2], ARGV[4])
                    return 1
                    """, Long.class);

    // Redis 명령을 실행할 객체
    private final StringRedisTemplate redisTemplate;
    private final long refreshExpirationMs;

    public RefreshTokenStore(
            StringRedisTemplate redisTemplate,
            @Value("${jwt.refresh-expiration}") long refreshExpirationMs) {
        this.redisTemplate = redisTemplate;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    // 회원 ID와 Refresh Token을 Redis에 저장
    public void save(Long memberId, String token) {
        // RedistTemplate에서 제공하는 메서드 opsForValue
        redisTemplate.opsForValue().set(
                key(memberId),
                token,
                Duration.ofMillis(refreshExpirationMs));
    }

    // 회원 ID에 저장된 Refresh Token을 조회
    public String find(Long memberId) {
        // 값이 있으면 String Refresh Token, 없거나 TTL종료시 null
        return redisTemplate.opsForValue().get(key(memberId));
    }

    // 회원 ID에 저장된 Refresh Token 삭제 - 로그아웃
    public void delete(Long memberId) {
        redisTemplate.delete(key(memberId));
    }

    public String getOrCreateGeneration(Long memberId) {
        return redisTemplate.execute(
                GET_OR_CREATE_GENERATION_SCRIPT,
                List.of(generationKey(memberId)),
                UUID.randomUUID().toString(),
                String.valueOf(refreshExpirationMs));
    }

    public String findGeneration(Long memberId) {
        return redisTemplate.opsForValue().get(generationKey(memberId));
    }

    public boolean rotate(
            Long memberId,
            String expectedToken,
            String newToken,
            String authGeneration) {
        Long result = redisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(key(memberId), generationKey(memberId)),
                expectedToken,
                newToken,
                authGeneration,
                String.valueOf(refreshExpirationMs));
        return Long.valueOf(1L).equals(result);
    }

    public void revokeAll(Long memberId) {
        redisTemplate.delete(List.of(key(memberId), generationKey(memberId)));
    }

    // Redis key 생성
    private String key(Long memberId) {
        return KEY_PREFIX + memberId;
    }

    private String generationKey(Long memberId) {
        return GENERATION_KEY_PREFIX + memberId;
    }
}
