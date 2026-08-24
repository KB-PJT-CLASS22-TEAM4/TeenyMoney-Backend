package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FreeSavingMonthlyVO;
import com.teenyfin.teenymoney.domain.teenyscore.dto.request.TeenyScoreChangeRequestDTO;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScoreChangeService;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScorePolicyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.mockito.Mockito.*;

class FreeSavingMonthlyScoreProcessorTest {
    @Test
    @DisplayName("자유적금은 해당 회차 납입액을 약정액과 비교해 점수를 한 번 요청한다")
    void appliesMonthlyPaymentRate() {
        FinancialProductMapper mapper = mock(FinancialProductMapper.class);
        TeenyScorePolicyService policy = mock(TeenyScorePolicyService.class);
        TeenyScoreChangeService changeService = mock(TeenyScoreChangeService.class);
        FreeSavingMonthlyScoreProcessor processor = new FreeSavingMonthlyScoreProcessor(
                mapper, policy, changeService, new FreeSavingCycleCalculator());
        LocalDate dueDate = LocalDate.of(2026, 8, 20);
        FreeSavingMonthlyVO saving = new FreeSavingMonthlyVO();
        saving.setEnrollmentId(7L);
        saving.setChildId(2L);
        saving.setMonthlyAmount(100_000L);
        saving.setStartDate(LocalDate.of(2026, 8, 1));
        saving.setPaymentDay(20);
        saving.setMaturityDate(LocalDate.of(2027, 8, 1));
        when(mapper.selectFreeSavingMonthlyForUpdate(7L, dueDate))
                .thenReturn(saving);
        when(mapper.selectSavingPaidAmountBetween(
                7L, LocalDate.of(2026, 8, 1).atStartOfDay(),
                LocalDate.of(2026, 8, 21).atStartOfDay()))
                .thenReturn(75_000L);
        TeenyScoreChangeRequestDTO request = mock(TeenyScoreChangeRequestDTO.class);
        when(policy.freeSavingMonthlyResult(2L, 7L, 1, 75))
                .thenReturn(request);

        processor.process(7L, dueDate);

        verify(policy).freeSavingMonthlyResult(2L, 7L, 1, 75);
        verify(changeService).change(request);
    }

    @Test
    @DisplayName("자유적금 회차 확정 대상이 아니면 점수를 변경하지 않는다")
    void skipsMissingTarget() {
        FinancialProductMapper mapper = mock(FinancialProductMapper.class);
        TeenyScorePolicyService policy = mock(TeenyScorePolicyService.class);
        TeenyScoreChangeService changeService = mock(TeenyScoreChangeService.class);
        FreeSavingMonthlyScoreProcessor processor = new FreeSavingMonthlyScoreProcessor(
                mapper, policy, changeService, new FreeSavingCycleCalculator());

        processor.process(7L, LocalDate.of(2026, 8, 20));

        verifyNoInteractions(policy, changeService);
    }

    @Test
    @DisplayName("자유적금 회차 납입액이 0원이면 MISSED만 기록하고 점수를 올리지 않는다")
    void recordsMissedWithoutScoreWhenNothingWasPaid() {
        FinancialProductMapper mapper = mock(FinancialProductMapper.class);
        TeenyScorePolicyService policy = mock(TeenyScorePolicyService.class);
        TeenyScoreChangeService changeService = mock(TeenyScoreChangeService.class);
        FreeSavingMonthlyScoreProcessor processor = new FreeSavingMonthlyScoreProcessor(
                mapper, policy, changeService, new FreeSavingCycleCalculator());
        LocalDate dueDate = LocalDate.of(2026, 8, 20);
        FreeSavingMonthlyVO saving = new FreeSavingMonthlyVO();
        saving.setEnrollmentId(7L);
        saving.setChildId(2L);
        saving.setMonthlyAmount(100_000L);
        saving.setPaymentDay(20);
        saving.setStartDate(LocalDate.of(2026, 8, 1));
        when(mapper.selectFreeSavingMonthlyForUpdate(7L, dueDate))
                .thenReturn(saving);

        processor.process(7L, dueDate);

        verify(mapper).insertSavingPaymentHistory(
                7L, null, 1, 100_000L, 0L, "MISSED");
        verifyNoInteractions(policy, changeService);
    }
}
