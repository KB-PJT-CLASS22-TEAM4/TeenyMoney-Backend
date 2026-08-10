package com.teenyfin.teenymoney.domain.teenyscore.service;

import com.teenyfin.teenymoney.domain.teenyscore.dto.request.TeenyScoreChangeRequestDTO;
import com.teenyfin.teenymoney.domain.teenyscore.dto.response.TeenyScoreChangeResponseDTO;
import com.teenyfin.teenymoney.domain.teenyscore.exception.TeenyScoreErrorCode;
import com.teenyfin.teenymoney.domain.teenyscore.mapper.TeenyScoreMapper;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreEventCode;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("티니점수 공통 변경 서비스")
class TeenyScoreChangeServiceTest {

    private TeenyScoreMapper teenyScoreMapper;
    private TeenyScoreChangeService teenyScoreChangeService;

    @BeforeEach
    void setUp() {
        teenyScoreMapper = mock(TeenyScoreMapper.class);
        teenyScoreChangeService = new TeenyScoreChangeService(
                teenyScoreMapper);
    }

    @Test
    @DisplayName("점수를 변경하고 실제 반영량과 변경 후 점수를 이력으로 저장한다")
    void changeUpdatesScoreAndStoresActualChangeInOneRequest() {
        TeenyScoreChangeRequestDTO request = request(10, "EVENT:1");
        when(teenyScoreMapper.selectScoreForUpdate(2L)).thenReturn(600);

        TeenyScoreChangeResponseDTO response =
                teenyScoreChangeService.change(request);

        assertTrue(response.isApplied());
        assertEquals(600, response.getScoreBefore());
        assertEquals(10, response.getRequestedAmount());
        assertEquals(10, response.getAppliedAmount());
        assertEquals(610, response.getScoreAfter());
        verify(teenyScoreMapper).updateTeenyScore(2L, 610);
        verify(teenyScoreMapper).insertScoreHistory(
                2L,
                10,
                610,
                "DEPOSIT_MATURED",
                "EVENT:1",
                "예금 만기 달성",
                "DEPOSIT_ENROLLMENT",
                11L);
    }

    @Test
    @DisplayName("1000점 초과 요청은 상한으로 보정하고 실제 반영량만 저장한다")
    void changeClampsScoreAtUpperBoundaryAndStoresAppliedAmount() {
        TeenyScoreChangeRequestDTO request = request(20, "EVENT:2");
        when(teenyScoreMapper.selectScoreForUpdate(2L)).thenReturn(995);

        TeenyScoreChangeResponseDTO response =
                teenyScoreChangeService.change(request);

        assertEquals(20, response.getRequestedAmount());
        assertEquals(5, response.getAppliedAmount());
        assertEquals(1000, response.getScoreAfter());
        verify(teenyScoreMapper).updateTeenyScore(2L, 1000);
        verify(teenyScoreMapper).insertScoreHistory(
                2L, 5, 1000, "DEPOSIT_MATURED", "EVENT:2",
                "예금 만기 달성", "DEPOSIT_ENROLLMENT", 11L);
    }

    @Test
    @DisplayName("0점 미만 요청은 하한으로 보정한다")
    void changeClampsScoreAtLowerBoundary() {
        TeenyScoreChangeRequestDTO request = request(-20, "EVENT:3");
        when(teenyScoreMapper.selectScoreForUpdate(2L)).thenReturn(5);

        TeenyScoreChangeResponseDTO response =
                teenyScoreChangeService.change(request);

        assertEquals(-5, response.getAppliedAmount());
        assertEquals(0, response.getScoreAfter());
        verify(teenyScoreMapper).updateTeenyScore(2L, 0);
        verify(teenyScoreMapper).insertScoreHistory(
                2L, -5, 0, "DEPOSIT_MATURED", "EVENT:3",
                "예금 만기 달성", "DEPOSIT_ENROLLMENT", 11L);
    }

    @Test
    @DisplayName("동일한 이벤트 키는 점수와 이력을 중복 반영하지 않는다")
    void duplicateEventDoesNotUpdateOrInsertAgain() {
        TeenyScoreChangeRequestDTO request = request(10, "EVENT:4");
        when(teenyScoreMapper.selectScoreForUpdate(2L)).thenReturn(610);
        when(teenyScoreMapper.existsHistoryByEventKey(2L, "EVENT:4"))
                .thenReturn(true);

        TeenyScoreChangeResponseDTO response =
                teenyScoreChangeService.change(request);

        assertFalse(response.isApplied());
        assertEquals(610, response.getScoreBefore());
        assertEquals(0, response.getAppliedAmount());
        assertEquals(610, response.getScoreAfter());
        verify(teenyScoreMapper, never()).updateTeenyScore(2L, 620);
        verify(teenyScoreMapper, never()).insertScoreHistory(
                2L, 10, 620, "DEPOSIT_MATURED", "EVENT:4",
                "예금 만기 달성", "DEPOSIT_ENROLLMENT", 11L);
    }

    @Test
    void sameDayBlockPaymentIsAppliedOnlyOnce() {
        TeenyScorePolicyService policyService =
                new TeenyScorePolicyService();
        TeenyScoreChangeRequestDTO first = policyService.blockPayment(
                2L, 100L, LocalDate.of(2026, 8, 10));
        TeenyScoreChangeRequestDTO second = policyService.blockPayment(
                2L, 101L, LocalDate.of(2026, 8, 10));
        when(teenyScoreMapper.selectScoreForUpdate(2L))
                .thenReturn(600, 580);
        when(teenyScoreMapper.existsHistoryByEventKey(
                2L, "PAYMENT_BLOCKED:2026-08-10"))
                .thenReturn(false, true);

        TeenyScoreChangeResponseDTO firstResult =
                teenyScoreChangeService.change(first);
        TeenyScoreChangeResponseDTO secondResult =
                teenyScoreChangeService.change(second);

        assertTrue(firstResult.isApplied());
        assertEquals(580, firstResult.getScoreAfter());
        assertFalse(secondResult.isApplied());
        assertEquals(580, secondResult.getScoreAfter());
        verify(teenyScoreMapper, times(1)).insertScoreHistory(
                2L, -20, 580, "PAYMENT_BLOCKED",
                "PAYMENT_BLOCKED:2026-08-10",
                "BLOCK 업종 결제 시도", "PAYMENT", 100L);
    }

    @Test
    void loanDefaultIsSeparateFromMonthlyOverdueAndAppliedOnlyOnce() {
        TeenyScorePolicyService policyService =
                new TeenyScorePolicyService();
        TeenyScoreChangeRequestDTO overdue = policyService.loanOverdue(
                2L, 20L, YearMonth.of(2026, 8),
                0, 10_000, 0);
        TeenyScoreChangeRequestDTO defaulted =
                policyService.loanDefault(2L, 20L);
        when(teenyScoreMapper.selectScoreForUpdate(2L))
                .thenReturn(600, 592, 572);
        when(teenyScoreMapper.existsHistoryByEventKey(
                2L, "LOAN_OVERDUE:20:2026-08"))
                .thenReturn(false);
        when(teenyScoreMapper.existsHistoryByEventKey(
                2L, "LOAN_DEFAULTED:20"))
                .thenReturn(false, true);

        TeenyScoreChangeResponseDTO overdueResult =
                teenyScoreChangeService.change(overdue);
        TeenyScoreChangeResponseDTO defaultResult =
                teenyScoreChangeService.change(defaulted);
        TeenyScoreChangeResponseDTO duplicateResult =
                teenyScoreChangeService.change(defaulted);

        assertEquals(-8, overdueResult.getAppliedAmount());
        assertEquals(592, overdueResult.getScoreAfter());
        assertEquals(-20, defaultResult.getAppliedAmount());
        assertEquals(572, defaultResult.getScoreAfter());
        assertFalse(duplicateResult.isApplied());
        assertEquals(572, duplicateResult.getScoreAfter());
        verify(teenyScoreMapper, times(1)).insertScoreHistory(
                2L, -20, 572, "LOAN_DEFAULTED",
                "LOAN_DEFAULTED:20", "대출 최종 만기 미상환",
                "LOAN_ENROLLMENT", 20L);
    }

    @Test
    @DisplayName("경계값으로 실제 반영량이 0이어도 중복 방지 이력은 저장한다")
    void zeroAppliedAmountStillStoresEventForIdempotency() {
        TeenyScoreChangeRequestDTO request = request(10, "EVENT:5");
        when(teenyScoreMapper.selectScoreForUpdate(2L)).thenReturn(1000);

        TeenyScoreChangeResponseDTO response =
                teenyScoreChangeService.change(request);

        assertEquals(0, response.getAppliedAmount());
        verify(teenyScoreMapper, never()).updateTeenyScore(2L, 1000);
        verify(teenyScoreMapper).insertScoreHistory(
                2L, 0, 1000, "DEPOSIT_MATURED", "EVENT:5",
                "예금 만기 달성", "DEPOSIT_ENROLLMENT", 11L);
    }

    @Test
    @DisplayName("존재하지 않는 자녀는 도메인 예외를 발생시키고 이력을 저장하지 않는다")
    void missingChildThrowsDomainExceptionWithoutWritingHistory() {
        TeenyScoreChangeRequestDTO request = request(10, "EVENT:6");
        when(teenyScoreMapper.selectScoreForUpdate(2L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> teenyScoreChangeService.change(request));

        assertEquals(
                TeenyScoreErrorCode.TEENY_SCORE_CHILD_NOT_FOUND,
                exception.getErrorCode());
        verify(teenyScoreMapper, never()).existsHistoryByEventKey(2L, "EVENT:6");
    }

    @Test
    @DisplayName("점수 변경 메서드는 트랜잭션으로 실행된다")
    void changeMethodIsTransactional() throws NoSuchMethodException {
        Method method = TeenyScoreChangeService.class.getMethod(
                "change", TeenyScoreChangeRequestDTO.class);

        assertNotNull(method.getAnnotation(Transactional.class));
    }

    private TeenyScoreChangeRequestDTO request(
            int amount,
            String eventKey) {
        return new TeenyScoreChangeRequestDTO(
                2L,
                TeenyScoreEventCode.DEPOSIT_MATURED,
                amount,
                eventKey,
                "예금 만기 달성",
                "DEPOSIT_ENROLLMENT",
                11L);
    }
}
