package com.teenyfin.teenymoney.domain.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teenyfin.teenymoney.domain.payment.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

@RequiredArgsConstructor
public class OrderStore {

    // redis에 payment:info:값 구조로 저장하기 때문에 접두사로 지정
    private static final String KEY_PREFIX = "payment:info:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // 결제 시도 ID로 결제 정보를 Redis에 저장 (ttl만큼만 유효)
    public void save(String paymentInfoId, OrderVO orderVO, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(orderVO);
            redisTemplate.opsForValue().set(key(paymentInfoId), json, ttl);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("결제 시도 정보 직렬화에 실패했습니다.", e);
        }
    }

    // 결제 시도 ID로 저장된 결제 정보를 조회
    public OrderVO find(String paymentInfoId) {
        String json = redisTemplate.opsForValue().get(key(paymentInfoId));
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, OrderVO.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("결제 시도 정보 역직렬화에 실패했습니다.", e);
        }
    }

    // 결제 확정/취소 후 시도 정보 삭제
    public void delete(String paymentInfoId) {
        redisTemplate.delete(key(paymentInfoId));
    }

    // Redis key 생성
    private String key(String paymentInfoId) {
        return KEY_PREFIX + paymentInfoId;
    }
}
