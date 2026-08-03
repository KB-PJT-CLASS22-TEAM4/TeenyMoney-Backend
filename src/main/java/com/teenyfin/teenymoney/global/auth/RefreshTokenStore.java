package com.teenyfin.teenymoney.global.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
/*
* Refresh Token을 Redis에 저장, 조회, 삭제 할 수 있습니다.
*
*
*/

@Component
public class RefreshTokenStore {

    // redis에 refresh:값 구조로 저장하기 때문에 접두사로 지정
    private static final String KEY_PREFIX = "refresh:";

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

    // Redis key 생성
    private String key(Long memberId) {
        return KEY_PREFIX + memberId;
    }
}
