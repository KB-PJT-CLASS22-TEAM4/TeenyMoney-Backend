package com.teenyfin.teenymoney.domain.family.service;

import com.teenyfin.teenymoney.domain.family.store.FamilyLinkCodeStore;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 발급 정책을 검증한다 - 멱등 키 우선순위, 쿨다운 게이트, 충돌 재시도, 소진 시 503.
 *
 * '이전 코드 삭제 + 예약 + 기록 + 멱등 기록'의 원자성은 Lua 스크립트가 보장하므로
 * 여기서 다루지 않는다. 실제 동작은 FamilyLinkCodeStoreRedisTest가 진짜 Redis로 본다.
 */
class FamilyLinkCodeServiceTest {

    private static final Long PARENT_ID = 17L;
    private static final Long CHILD_ID = 33L;
    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final Instant NOW = Instant.parse("2026-08-06T00:00:00Z");
    private static final String IDEM = "intent-1";

    private MemberMapper memberMapper;
    private FamilyLinkCodeStore store;
    private FamilyLinkCodeService service;

    @BeforeEach
    void setUp() {
        store = mock(FamilyLinkCodeStore.class);
        memberMapper = mock(MemberMapper.class);
        service = new FamilyLinkCodeService(
                store,
                memberMapper,
                Clock.fixed(NOW, ZoneId.of("Asia/Seoul")));

        // 기본값: 남은 TTL은 꽉 찬 10분
        when(store.remainingTtl(anyString())).thenReturn(CODE_TTL);
    }

    private void cooldownPasses() {
        when(store.tryStartCooldown(eq(PARENT_ID), any())).thenReturn(true);
    }

    /** 스크립트는 발급에 성공하면 인자로 받은 코드를 그대로 돌려준다. */
    private void issueSucceeds() {
        when(store.tryIssueCode(eq(PARENT_ID), anyString(), anyString(), eq(CODE_TTL)))
                .thenAnswer(invocation -> invocation.getArgument(1));
    }

    @Test
    @DisplayName("발급에 성공하면 6자리 코드와 남은 TTL 기준 만료 시각을 돌려준다")
    void issuesSixDigitCodeWithExpiryFromRemainingTtl() {
        cooldownPasses();
        issueSucceeds();

        var response = service.makeCode(PARENT_ID, IDEM);

        assertTrue(response.code().matches("\\d{6}"), "6자리 숫자가 아님: " + response.code());
        assertEquals(
                OffsetDateTime.ofInstant(NOW, ZoneId.of("Asia/Seoul")).plus(CODE_TTL),
                response.expiresAt());
    }

    @Test
    @DisplayName("같은 멱등 키로 다시 오면 발급하지 않고 그때의 코드를 돌려준다")
    void sameIdempotencyKeyReturnsTheSameCode() {
        when(store.findIssuedCode(PARENT_ID, IDEM)).thenReturn("048291");
        when(store.remainingTtl("048291")).thenReturn(Duration.ofMinutes(4));

        var response = service.makeCode(PARENT_ID, IDEM);

        assertEquals("048291", response.code());
        // 재사용 코드는 발급 시각이 과거다. 지금+10분이 아니라 남은 TTL로 계산해야 한다.
        assertEquals(
                OffsetDateTime.ofInstant(NOW, ZoneId.of("Asia/Seoul")).plusMinutes(4),
                response.expiresAt());

        verify(store, never()).tryIssueCode(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("멱등 재사용은 쿨다운보다 먼저 판정된다 (재시도가 429를 받으면 안 된다)")
    void idempotentReplayIsNotBlockedByCooldown() {
        when(store.tryStartCooldown(eq(PARENT_ID), any())).thenReturn(false);
        when(store.findIssuedCode(PARENT_ID, IDEM)).thenReturn("048291");

        assertEquals("048291", service.makeCode(PARENT_ID, IDEM).code());

        verify(store, never()).tryStartCooldown(any(), any());
    }

    @Test
    @DisplayName("멱등 키가 비어 있으면 아무것도 건드리지 않고 400으로 거절한다")
    void rejectsBlankIdempotencyKey() {
        for (String key : Arrays.asList(null, "", "   ")) {
            BusinessException e = assertThrows(
                    BusinessException.class,
                    () -> service.makeCode(PARENT_ID, key),
                    "거절되지 않은 값: [" + key + "]");

            assertEquals("FAMILY_IDEMPOTENCY_KEY_INVALID", e.getErrorCode().getCode());
            assertEquals(400, e.getErrorCode().getStatus().value());
        }

        // 서버가 키를 대신 만들어 발급해버리면 보장 없이 조용히 동작한다.
        verify(store, never()).tryIssueCode(any(), anyString(), anyString(), any());
        verify(store, never()).tryStartCooldown(any(), any());
    }

    @Test
    @DisplayName("멱등 키가 100자를 넘으면 400으로 거절한다 (Redis 키가 비대해진다)")
    void rejectsOverlongIdempotencyKey() {
        String tooLong = "x".repeat(101);

        BusinessException e = assertThrows(
                BusinessException.class,
                () -> service.makeCode(PARENT_ID, tooLong));

        assertEquals("FAMILY_IDEMPOTENCY_KEY_INVALID", e.getErrorCode().getCode());
        verify(store, never()).tryIssueCode(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("100자 정확히는 통과한다 (경계값)")
    void acceptsIdempotencyKeyAtExactlyMaxLength() {
        cooldownPasses();
        issueSucceeds();

        assertTrue(service.makeCode(PARENT_ID, "x".repeat(100)).code().matches("\\d{6}"));
    }

    @Test
    @DisplayName("쿨다운에 걸리면 발급을 시도하지 않고 429로 끊는다")
    void rejectsWithoutIssuingWhenCooldownActive() {
        when(store.tryStartCooldown(eq(PARENT_ID), any())).thenReturn(false);

        BusinessException e = assertThrows(
                BusinessException.class,
                () -> service.makeCode(PARENT_ID, IDEM));

        assertEquals("FAMILY_LINK_CODE_TOO_SOON", e.getErrorCode().getCode());
        assertEquals(429, e.getErrorCode().getStatus().value());

        // 거절된 요청이 발급을 시도하면 살아있는 코드가 죽는다.
        verify(store, never()).tryIssueCode(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("코드가 충돌하면 다른 코드로 다시 시도한다")
    void retriesWithAnotherCodeOnCollision() {
        cooldownPasses();
        when(store.tryIssueCode(eq(PARENT_ID), anyString(), anyString(), eq(CODE_TTL)))
                .thenReturn(null)
                .thenAnswer(invocation -> invocation.getArgument(1));

        service.makeCode(PARENT_ID, IDEM);

        verify(store, times(2))
                .tryIssueCode(eq(PARENT_ID), anyString(), anyString(), eq(CODE_TTL));
    }

    @Test
    @DisplayName("10회 연속 충돌하면 503으로 끝난다")
    void failsWithServiceUnavailableAfterTenCollisions() {
        cooldownPasses();
        when(store.tryIssueCode(eq(PARENT_ID), anyString(), anyString(), eq(CODE_TTL)))
                .thenReturn(null);

        BusinessException e = assertThrows(
                BusinessException.class,
                () -> service.makeCode(PARENT_ID, IDEM));

        assertEquals("COMMON_SERVICE_UNAVAILABLE", e.getErrorCode().getCode());
        verify(store, times(10))
                .tryIssueCode(eq(PARENT_ID), anyString(), anyString(), eq(CODE_TTL));
    }

    @Test
    @DisplayName("소비에 실패한 코드는 400 FAMILY_LINK_CODE_INVALID로 거절된다")
    void rejectsUnknownCodeOnConsume() {
        when(store.consumeCode("000000")).thenReturn(null);

        BusinessException e = assertThrows(
                BusinessException.class,
                () -> service.consumeCode("000000"));

        assertEquals("FAMILY_LINK_CODE_INVALID", e.getErrorCode().getCode());
        assertEquals(400, e.getErrorCode().getStatus().value());
    }

    @Test
    @DisplayName("소비에 성공하면 부모 ID를 돌려준다")
    void returnsParentIdOnConsume() {
        when(store.consumeCode("048291")).thenReturn(PARENT_ID);

        assertEquals(PARENT_ID, service.consumeCode("048291"));
    }

    @Test
    @DisplayName("유효한 코드를 소비하면 부모-자녀 관계를 저장한다")
    void linksChildWithConsumedCode() {
        when(store.incrementConsumeAttempts(eq(CHILD_ID), any())).thenReturn(1L);
        when(store.consumeCode("048291")).thenReturn(PARENT_ID);
        when(memberMapper.insertConnection(PARENT_ID, CHILD_ID)).thenReturn(1);

        service.linkChild(CHILD_ID, "048291");

        verify(memberMapper).insertConnection(PARENT_ID, CHILD_ID);
    }

    @Test
    @DisplayName("이미 연결된 자녀는 다른 부모의 코드를 소비하지 않는다")
    void alreadyLinkedChildDoesNotConsumeCode() {
        when(memberMapper.existsActiveConnectionByChildId(CHILD_ID)).thenReturn(true);

        BusinessException e = assertThrows(
                BusinessException.class,
                () -> service.linkChild(CHILD_ID, "048291"));

        assertEquals("FAMILY_ALREADY_LINKED", e.getErrorCode().getCode());
        verify(store, never()).incrementConsumeAttempts(any(), any());
        verify(store, never()).consumeCode(anyString());
    }

    @Test
    @DisplayName("입력 횟수를 초과하면 코드를 소비하지 않는다")
    void attemptLimitDoesNotConsumeCode() {
        when(store.incrementConsumeAttempts(eq(CHILD_ID), any())).thenReturn(6L);

        BusinessException e = assertThrows(
                BusinessException.class,
                () -> service.linkChild(CHILD_ID, "048291"));

        assertEquals("FAMILY_LINK_TOO_MANY_ATTEMPTS", e.getErrorCode().getCode());
        verify(store, never()).consumeCode(anyString());
        verify(memberMapper, never()).insertConnection(any(), any());
    }

    @Test
    @DisplayName("역할 또는 상태가 유효하지 않으면 관계를 저장하지 않는다")
    void rejectsWhenGuardedInsertStoresNothing() {
        when(store.incrementConsumeAttempts(eq(CHILD_ID), any())).thenReturn(1L);
        when(store.consumeCode("048291")).thenReturn(PARENT_ID);
        when(memberMapper.insertConnection(PARENT_ID, CHILD_ID)).thenReturn(0);

        BusinessException e = assertThrows(
                BusinessException.class,
                () -> service.linkChild(CHILD_ID, "048291"));

        assertEquals("FAMILY_LINK_PARENT_UNAVAILABLE", e.getErrorCode().getCode());
    }

    @Test
    @DisplayName("동시 연결로 UNIQUE 제약에 걸리면 중복 연결 오류를 반환한다")
    void duplicateConnectionReturnsConflict() {
        when(store.incrementConsumeAttempts(eq(CHILD_ID), any())).thenReturn(1L);
        when(store.consumeCode("048291")).thenReturn(PARENT_ID);
        when(memberMapper.insertConnection(PARENT_ID, CHILD_ID))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("duplicate"));

        BusinessException e = assertThrows(
                BusinessException.class,
                () -> service.linkChild(CHILD_ID, "048291"));

        assertEquals("FAMILY_ALREADY_LINKED", e.getErrorCode().getCode());
    }
}
