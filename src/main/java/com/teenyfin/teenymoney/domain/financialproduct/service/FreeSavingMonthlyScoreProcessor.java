package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FreeSavingMonthlyVO;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScoreChangeService;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScorePolicyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;

/** 자유적금 한 가입의 한 달 납입률을 확정하고 월 단위 점수 이벤트를 반영한다. */
@Service
public class FreeSavingMonthlyScoreProcessor {
    private final FinancialProductMapper financialProductMapper;
    private final TeenyScorePolicyService scorePolicyService;
    private final TeenyScoreChangeService scoreChangeService;

    public FreeSavingMonthlyScoreProcessor(
            FinancialProductMapper financialProductMapper,
            TeenyScorePolicyService scorePolicyService,
            TeenyScoreChangeService scoreChangeService) {
        this.financialProductMapper = financialProductMapper;
        this.scorePolicyService = scorePolicyService;
        this.scoreChangeService = scoreChangeService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(Long enrollmentId, YearMonth targetMonth) {
        LocalDate monthStart = targetMonth.atDay(1);
        LocalDate monthEnd = targetMonth.plusMonths(1).atDay(1);
        FreeSavingMonthlyVO saving = financialProductMapper
                .selectFreeSavingMonthlyForUpdate(enrollmentId, monthStart, monthEnd);
        if (saving == null) return;

        // 자유적금은 여러 번 납입할 수 있으므로 해당 월의 성공 납입액을 모두 합산한다.
        long paidAmount = financialProductMapper.selectSavingPaidAmountBetween(
                enrollmentId, monthStart.atStartOfDay(), monthEnd.atStartOfDay());
        int paymentRate = saving.getMonthlyAmount() == null
                || saving.getMonthlyAmount() <= 0
                ? 0
                : (int) Math.min(100,
                        paidAmount * 100 / saving.getMonthlyAmount());
        // 정책 서비스가 enrollmentId + YYYY-MM 이벤트 키를 생성해 재실행 시 중복 점수를 막는다.
        scoreChangeService.change(scorePolicyService.freeSavingMonthlyResult(
                saving.getChildId(), enrollmentId, targetMonth, paymentRate));
    }
}
