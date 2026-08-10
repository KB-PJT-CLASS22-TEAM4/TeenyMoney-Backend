package com.teenyfin.teenymoney.domain.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teenyfin.teenymoney.domain.payment.vo.PaymentInfoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

@RequiredArgsConstructor
public class PaymentInfoStore {

    // redis에 payment:info:값 구조로 저장하기 때문에 접두사로 지정
    private static final String KEY_PREFIX = "payment:info:";
    private static final String LOCK_PREFIX = "payment:lock:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // 결제 시도 ID로 결제 정보를 Redis에 저장 (ttl만큼만 유효)
    public void save(String paymentInfoId, PaymentInfoVO paymentInfoVO, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(paymentInfoVO);
            redisTemplate.opsForValue().set(key(paymentInfoId), json, ttl);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("결제 시도 정보 직렬화에 실패했습니다.", e);
        }
    }

    // 결제 시도 ID로 저장된 결제 정보를 조회
    public PaymentInfoVO find(String paymentInfoId) {
        String json = redisTemplate.opsForValue().get(key(paymentInfoId));
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, PaymentInfoVO.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("결제 시도 정보 역직렬화에 실패했습니다.", e);
        }
    }

    // 결제 확정/취소 후 시도 정보 삭제
    public void delete(String paymentInfoId) {
        redisTemplate.delete(key(paymentInfoId));
    }

    // 가맹점 이름 + 금액 조합에 대한 동시 결제 시도를 막기 위한 락 획득 시도
    public boolean tryLock(String merchantName, Long amount, Duration ttl) {
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(lockKey(merchantName, amount), "LOCKED", ttl);
        return Boolean.TRUE.equals(success);
    }

    // 락 해제
    public void unlock(String merchantName, Long amount) {
        redisTemplate.delete(lockKey(merchantName, amount));
    }

    // Redis key 생성
    private String key(String paymentInfoId) {
        return KEY_PREFIX + paymentInfoId;
    }

    private String lockKey(String merchantName, Long amount) {
        return LOCK_PREFIX + merchantName + ":" + amount;
    }
}
