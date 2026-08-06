package com.teenyfin.teenymoney.domain.family.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 키 이름 규칙과 스크립트 인자 전달을 검증한다.
 *
 * 스크립트가 무엇을 하는지는 진짜 Redis에서만 확인할 수 있다(FamilyLinkCodeStoreRedisTest).
 * 여기서 잡는 건 그 앞단 - 키를 제대로 만들어 넘기는가, 반환값을 제대로 해석하는가다.
 */
class FamilyLinkCodeStoreTest {

    private static final Long PARENT_ID = 17L;
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final String IDEM = "idem-1";

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private FamilyLinkCodeStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        store = new FamilyLinkCodeStore(redisTemplate);
    }

    @Test
    @DisplayName("발급 스크립트에 슬롯·코드·멱등 키를 순서대로 넘긴다")
    void issuePassesAllThreeKeysInOrder() {
        when(redisTemplate.execute(
                any(),
                eq(List.of(
                        "family-link:parent:17",
                        "family-link:code:048291",
                        "family-link:idem:17:idem-1")),
                eq("17"),
                eq("600000"),
                eq("family-link:code:"),
                eq("048291")))
                .thenReturn("048291");

        assertEquals("048291", store.tryIssueCode(PARENT_ID, "048291", IDEM, TTL));
    }

    @Test
    @DisplayName("멱등 재사용이면 인자로 준 코드가 아니라 그때의 코드가 돌아온다")
    void issueReturnsThePreviouslyIssuedCodeOnReplay() {
        when(redisTemplate.execute(any(), anyList(), any(), any(), any(), any()))
                .thenReturn("111111");

        assertEquals("111111", store.tryIssueCode(PARENT_ID, "048291", IDEM, TTL));
    }

    @Test
    @DisplayName("스크립트가 nil을 돌려주면 코드 충돌로 본다")
    void issueReturnsNullOnCollision() {
        when(redisTemplate.execute(any(), anyList(), any(), any(), any(), any()))
                .thenReturn(null);

        assertNull(store.tryIssueCode(PARENT_ID, "048291", IDEM, TTL));
    }

    @Test
    @DisplayName("멱등 키는 부모별로 격리된다")
    void idempotencyKeyIsScopedToParent() {
        when(valueOperations.get("family-link:idem:17:idem-1")).thenReturn("048291");

        assertEquals("048291", store.findIssuedCode(PARENT_ID, IDEM));
        assertNull(store.findIssuedCode(42L, IDEM));
    }

    @Test
    @DisplayName("쿨다운 키는 부모별로 분리되고 SET NX로 잡는다")
    void cooldownKeyIsPerParentAndSetIfAbsent() {
        when(valueOperations.setIfAbsent("family-link:cooldown:17", "1", TTL))
                .thenReturn(true);

        assertTrue(store.tryStartCooldown(PARENT_ID, TTL));
    }

    @Test
    @DisplayName("쿨다운 응답이 null이면 잡지 못한 것으로 본다")
    void cooldownReturnsFalseOnNullReply() {
        when(valueOperations.setIfAbsent("family-link:cooldown:17", "1", TTL))
                .thenReturn(null);

        assertFalse(store.tryStartCooldown(PARENT_ID, TTL));
    }

    @Test
    @DisplayName("소비는 GETDEL 한 번으로 처리한다")
    void consumeUsesSingleGetAndDelete() {
        when(valueOperations.getAndDelete("family-link:code:048291")).thenReturn("17");

        assertEquals(PARENT_ID, store.consumeCode("048291"));

        // get 후 delete로 나누면 동시 소비 시 둘 다 부모 ID를 받는다.
        verify(redisTemplate, never()).delete("family-link:code:048291");
    }

    @Test
    @DisplayName("없는 코드를 소비하면 null이다")
    void consumeReturnsNullForUnknownCode() {
        when(valueOperations.getAndDelete("family-link:code:000000")).thenReturn(null);

        assertNull(store.consumeCode("000000"));
    }

    @Test
    @DisplayName("남은 TTL이 없으면 0을 돌려준다")
    void remainingTtlIsZeroWhenKeyGone() {
        when(redisTemplate.getExpire(eq("family-link:code:048291"), any()))
                .thenReturn(-2L);

        assertEquals(Duration.ZERO, store.remainingTtl("048291"));
    }

    @Test
    @DisplayName("코드 입력 시도 증가와 최초 TTL 설정을 Redis 스크립트 한 번으로 처리한다")
    void consumeAttemptIncrementAndExpiryUseSingleRedisExecution() {
        when(redisTemplate.execute(
                any(),
                eq(List.of("family-link:attempts:33")),
                eq("600000")))
                .thenReturn(2L);

        assertEquals(2L, store.incrementConsumeAttempts(33L, TTL));

        verify(redisTemplate, never()).expire(any(), any(Duration.class));
        verify(valueOperations, never()).increment(any());
    }
}
