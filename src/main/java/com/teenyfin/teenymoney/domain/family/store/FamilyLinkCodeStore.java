package com.teenyfin.teenymoney.domain.family.store;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class FamilyLinkCodeStore {

    private static final String CODE_PREFIX = "family-link:code:";
    private static final String PARENT_PREFIX = "family-link:parent:";

    private final StringRedisTemplate redisTemplate;

    public FamilyLinkCodeStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    //setIfAbsent - Redis의 SET NX, 해당 코드가 없으면 저장후 true, 이미 발급된 코드면 저장X + false
    public boolean reserveCode(String code, Long parentId, Duration ttl) {
        Boolean reserved = redisTemplate.opsForValue().setIfAbsent(
                codeKey(code),
                parentId.toString(),
                ttl
        );

        // redis의 응답이 null일수도 있어서
        return Boolean.TRUE.equals(reserved);
    }

    // 부모가 발급한 현재 코드를 저장한다. - family-link:parent:17 -> 048291
    // 새로 발급 시, 덮어씌워진다 (계속 늘어나는 구조아님)
    public void saveCurrentCode(
            Long parentId,
            String code,
            Duration ttl
    ) {
        redisTemplate.opsForValue().set(
                parentKey(parentId.toString()),
                code,
                ttl
        );
    }

    // 유효한 코드 조회
    public Long findValidParentId(String code) {
        // 부모키 조회(code를 이용해 redis에서 꺼냄)
        String parentId = redisTemplate.opsForValue().get(codeKey(code));

        if (parentId == null) {
            return null;
        }

        // redis에서 부모키에 대한 코드 받아옴 -> 입력한 코드와 같은지 검증
        String currentCode = redisTemplate.opsForValue().get(
                parentKey(parentId)
        );

        if (!code.equals(currentCode)) {
            return null;
        }

        return Long.valueOf(parentId);
    }

    private String codeKey(String code) {
        return CODE_PREFIX + code;
    }

    private String parentKey(String parentId) {
        return PARENT_PREFIX + parentId;
    }
}