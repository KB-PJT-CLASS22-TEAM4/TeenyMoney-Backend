package com.teenyfin.teenymoney.domain.family.store;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 가족 연동 코드의 Redis 저장소.
 *
 * 키가 셋이고 역할이 서로 다르다.
 *   family-link:code:{code}    -> parentId   코드의 유일한 권위. 있으면 유효, 없으면 무효
 *   family-link:parent:{id}    -> code       현재 코드. 재발급 때 무엇을 지울지 안다
 *   family-link:cooldown:{id}  -> "1"        발급 남용 제한
 *
 * 발급만 Lua다. '이전 코드 삭제 + 새 코드 예약 + 현재 코드 기록' 세 쓰기가 하나처럼
 * 일어나야 하는데, 이건 명령 하나로 표현할 수 없다. 나머지는 전용 명령으로 충분하므로
 * 스크립트를 쓰지 않는다(소비는 GETDEL, 쿨다운은 SET NX).
 */
@Component
public class FamilyLinkCodeStore {

    private static final String CODE_PREFIX = "family-link:code:";
    private static final String PARENT_PREFIX = "family-link:parent:";
    private static final String COOLDOWN_PREFIX = "family-link:cooldown:";
    private static final String IDEMPOTENCY_PREFIX = "family-link:idem:";
    private static final String ATTEMPTS_PREFIX = "family-link:attempts:";

    /**
     * 코드 발급을 통째로 원자화한다. 발급되거나 재사용된 코드를 돌려주고,
     * 코드가 충돌하면 nil(자바에서 null)을 돌려준다 - 호출측이 다시 뽑는다.
     *
     * KEYS[1] 부모의 현재 코드 슬롯   KEYS[2] 새 코드 키   KEYS[3] 멱등 키
     * ARGV[1] parentId   ARGV[2] TTL(ms)   ARGV[3] 코드 키 접두사   ARGV[4] 새 코드
     *
     * 멱등 키가 이미 있으면 그때 발급한 코드를 그대로 돌려준다. 타임아웃 재시도가
     * 같은 키로 다시 오면 새 코드를 만들지 않으므로, 늦게 도착한 첫 응답과 재시도 응답이
     * 같은 값을 싣는다. 쿨다운은 빈도만 줄일 뿐 이걸 보장하지 못한다.
     *
     * 같은 키로 두 요청이 동시에 와도 안전하다. 스크립트가 직렬 실행되므로 뒤에 오는
     * 요청은 앞 요청이 남긴 멱등 키를 보게 된다.
     *
     * 스크립트는 롤백이 없다. 중간에 에러가 나면 이미 실행된 명령은 남는다.
     * 그래서 실패할 수 있는 검사(EXISTS)를 맨 앞에 두고 쓰기를 뒤로 몰았다.
     *
     * 이전 코드 키는 슬롯을 읽어야 이름을 알 수 있어 KEYS에 넣지 못한다.
     * 단일 노드 Redis라 문제없지만, Cluster로 가면 해시태그가 필요해진다.
     *
     * 이전 코드를 지우기 전에 소유권을 반드시 확인한다. 슬롯이 항상 내 코드를 가리킨다고
     * 볼 수 없기 때문이다 - consumeCode는 코드 키만 지우고 슬롯은 남기므로, 소비된 뒤
     * 슬롯에는 죽은 코드 이름이 남는다. 그 사이 다른 부모가 같은 번호를 뽑아 예약하면
     * 슬롯이 '남의 유효한 코드'를 가리키게 되고, 확인 없이 지우면 그 부모의 코드가 사라진다.
     * 확인과 삭제가 같은 스크립트 안에 있으므로 그 사이에 주인이 바뀔 창은 없다.
     */
    private static final DefaultRedisScript<String> ISSUE_SCRIPT =
            new DefaultRedisScript<>("""
                    local reused = redis.call('GET', KEYS[3])
                    if reused then
                        return reused
                    end
                    if redis.call('EXISTS', KEYS[2]) == 1 then
                        return false
                    end
                    local previous = redis.call('GET', KEYS[1])
                    if previous then
                        local previousKey = ARGV[3] .. previous
                        if redis.call('GET', previousKey) == ARGV[1] then
                            redis.call('DEL', previousKey)
                        end
                    end
                    redis.call('SET', KEYS[2], ARGV[1], 'PX', ARGV[2])
                    redis.call('SET', KEYS[1], ARGV[4], 'PX', ARGV[2])
                    redis.call('SET', KEYS[3], ARGV[4], 'PX', ARGV[2])
                    return ARGV[4]
                    """, String.class);

    private static final DefaultRedisScript<Long> INCREMENT_ATTEMPTS_SCRIPT =
            new DefaultRedisScript<>("""
                    local attempts = redis.call('INCR', KEYS[1])
                    if attempts == 1 then
                        redis.call('PEXPIRE', KEYS[1], ARGV[1])
                    end
                    return attempts
                    """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public FamilyLinkCodeStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 발급 남용 제한. SET NX라 확인과 등록이 한 번에 일어난다.
     *
     * 발급 자체는 스크립트가 원자적으로 처리하므로 이 자물쇠가 정합성을 책임지지는 않는다.
     * 목적은 두 가지다 - 발급 폭주 차단, 그리고 더블탭으로 코드가 연달아 발급돼
     * 화면에 이미 죽은 코드가 남는 상황 방지.
     */
    public boolean tryStartCooldown(Long parentId, Duration cooldown) {
        Boolean started = redisTemplate.opsForValue().setIfAbsent(
                cooldownKey(parentId),
                "1",
                cooldown
        );

        // redis 응답이 null일 수도 있어서
        return Boolean.TRUE.equals(started);
    }

    /**
     * 코드를 발급한다. 이전 코드는 이 호출과 함께 무효가 된다.
     *
     * 반환값은 실제로 유효해진 코드다 - 새로 발급했으면 인자로 준 code,
     * 같은 멱등 키로 이미 발급한 적이 있으면 그때의 코드다.
     *
     * null은 '그 코드를 이미 다른 부모가 쓰고 있다'는 뜻이며, 이때 이전 코드는
     * 그대로 살아 있다(스크립트가 EXISTS에서 아무것도 쓰지 않고 빠져나온다).
     */
    public String tryIssueCode(
            Long parentId,
            String code,
            String idempotencyKey,
            Duration ttl) {
        return redisTemplate.execute(
                ISSUE_SCRIPT,
                List.of(
                        parentKey(parentId),
                        codeKey(code),
                        idempotencyKey(parentId, idempotencyKey)),
                parentId.toString(),
                String.valueOf(ttl.toMillis()),
                CODE_PREFIX,
                code);
    }

    /** 이 멱등 키로 이미 발급한 코드. 없으면 null. */
    public String findIssuedCode(Long parentId, String idempotencyKey) {
        return redisTemplate.opsForValue().get(idempotencyKey(parentId, idempotencyKey));
    }

    /**
     * 코드의 남은 유효시간. 멱등 재사용으로 받은 코드는 발급 시각이 과거이므로
     * 만료 시각을 지금 기준으로 계산하면 실제보다 늦게 나온다.
     */
    public Duration remainingTtl(String code) {
        Long ms = redisTemplate.getExpire(codeKey(code), TimeUnit.MILLISECONDS);

        return ms == null || ms < 0 ? Duration.ZERO : Duration.ofMillis(ms);
    }

    /**
     * 코드를 읽으면서 지운다(GETDEL, Redis 6.2+).
     *
     * 조회와 삭제가 한 명령이라 자녀 둘이 같은 코드를 동시에 넣어도 값을 받는 쪽은 하나뿐이다.
     * 판단이 끼지 않는 단일 키 연산이라 여기엔 스크립트가 필요 없다.
     */
    public Long consumeCode(String code) {
        String parentId = redisTemplate.opsForValue().getAndDelete(codeKey(code));

        return parentId == null ? null : Long.valueOf(parentId);
    }

    private String codeKey(String code) {
        return CODE_PREFIX + code;
    }

    private String parentKey(Long parentId) {
        return PARENT_PREFIX + parentId;
    }

    private String cooldownKey(Long parentId) {
        return COOLDOWN_PREFIX + parentId;
    }

    /** 멱등 키는 부모별로 격리한다. 다른 회원이 같은 키 값을 보내도 서로 섞이지 않는다. */
    private String idempotencyKey(Long parentId, String idempotencyKey) {
        return IDEMPOTENCY_PREFIX + parentId + ":" + idempotencyKey;
    }

    public Long incrementConsumeAttempts(
            Long childId,
            Duration ttl
    ) {
        String key = ATTEMPTS_PREFIX + childId;
        return redisTemplate.execute(
                INCREMENT_ATTEMPTS_SCRIPT,
                List.of(key),
                String.valueOf(ttl.toMillis())
        );
    }
}
