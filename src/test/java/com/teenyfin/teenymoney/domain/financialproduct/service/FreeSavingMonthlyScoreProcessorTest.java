package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FreeSavingMonthlyVO;
import com.teenyfin.teenymoney.domain.teenyscore.dto.request.TeenyScoreChangeRequestDTO;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScoreChangeService;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScorePolicyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.mockito.Mockito.*;

class FreeSavingMonthlyScoreProcessorTest {
    @Test
    @DisplayName("자유적금은 해당 월 납입액을 monthlyAmount와 비교해 월 점수를 한 번 요청한다")
    void appliesMonthlyPaymentRate() {
        FinancialProductMapper mapper = mock(FinancialProductMapper.class);
        TeenyScorePolicyService policy = mock(TeenyScorePolicyService.class);
        TeenyScoreChangeService changeService = mock(TeenyScoreChangeService.class);
        FreeSavingMonthlyScoreProcessor processor = new FreeSavingMonthlyScoreProcessor(
                mapper, policy, changeService);
        YearMonth month = YearMonth.of(2026, 8);
        FreeSavingMonthlyVO saving = new FreeSavingMonthlyVO();
        saving.setEnrollmentId(7L);
        saving.setChildId(2L);
        saving.setMonthlyAmount(100_000L);
        saving.setStartDate(LocalDate.of(2026, 8, 1));
        saving.setMaturityDate(LocalDate.of(2027, 8, 1));
        when(mapper.selectFreeSavingMonthlyForUpdate(
                7L, month.atDay(1), month.plusMonths(1).atDay(1)))
                .thenReturn(saving);
        when(mapper.selectSavingPaidAmountBetween(anyLong(), any(), any()))
                .thenReturn(75_000L);
        TeenyScoreChangeRequestDTO request = mock(TeenyScoreChangeRequestDTO.class);
        when(policy.freeSavingMonthlyResult(2L, 7L, month, 75))
                .thenReturn(request);

        processor.process(7L, month);

        verify(policy).freeSavingMonthlyResult(2L, 7L, month, 75);
        verify(changeService).change(request);
    }

    @Test
    @DisplayName("자유적금 월 평가 대상이 아니면 점수를 변경하지 않는다")
    void skipsMissingTarget() {
        FinancialProductMapper mapper = mock(FinancialProductMapper.class);
        TeenyScorePolicyService policy = mock(TeenyScorePolicyService.class);
        TeenyScoreChangeService changeService = mock(TeenyScoreChangeService.class);
        FreeSavingMonthlyScoreProcessor processor = new FreeSavingMonthlyScoreProcessor(
                mapper, policy, changeService);

        processor.process(7L, YearMonth.of(2026, 8));

        verifyNoInteractions(policy, changeService);
    }
}
