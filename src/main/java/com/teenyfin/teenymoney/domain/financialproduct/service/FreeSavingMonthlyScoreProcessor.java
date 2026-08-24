package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FreeSavingMonthlyVO;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScoreChangeService;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScorePolicyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/** 지정 납입일이 지난 자유적금 회차의 미납과 점수를 확정한다. */
@Service
public class FreeSavingMonthlyScoreProcessor {
    private final FinancialProductMapper financialProductMapper;
    private final TeenyScorePolicyService scorePolicyService;
    private final TeenyScoreChangeService scoreChangeService;
    private final FreeSavingCycleCalculator cycleCalculator;

    public FreeSavingMonthlyScoreProcessor(
            FinancialProductMapper financialProductMapper,
            TeenyScorePolicyService scorePolicyService,
            TeenyScoreChangeService scoreChangeService,
            FreeSavingCycleCalculator cycleCalculator) {
        this.financialProductMapper = financialProductMapper;
        this.scorePolicyService = scorePolicyService;
        this.scoreChangeService = scoreChangeService;
        this.cycleCalculator = cycleCalculator;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(Long enrollmentId, LocalDate dueDate) {
        FreeSavingMonthlyVO saving = financialProductMapper
                .selectFreeSavingMonthlyForUpdate(enrollmentId, dueDate);
        if (saving == null) return;

        FreeSavingCycle cycle = cycleCalculator.forDueDate(
                saving.getStartDate(), saving.getPaymentDay(), dueDate);
        long paidAmount = financialProductMapper.selectSavingPaidAmountBetween(
                enrollmentId, cycle.startInclusive(), cycle.endExclusive());
        // 전혀 납입하지 않은 회차는 점수 없이 미납 이력만 남긴다.
        if (paidAmount == 0) {
            if (financialProductMapper.countSavingPaymentHistory(
                    enrollmentId, cycle.installmentNo()) == 0) {
                financialProductMapper.insertSavingPaymentHistory(
                        enrollmentId, null, cycle.installmentNo(),
                        saving.getMonthlyAmount(), 0L, "MISSED");
            }
            return;
        }
        int paymentRate = saving.getMonthlyAmount() == null
                || saving.getMonthlyAmount() <= 0
                ? 0
                : (int) Math.min(100,
                        paidAmount * 100 / saving.getMonthlyAmount());
        // 계약 ID와 회차 번호로 만든 키가 재실행 시 중복 점수를 막는다.
        scoreChangeService.change(scorePolicyService.freeSavingMonthlyResult(
                saving.getChildId(), enrollmentId,
                cycle.installmentNo(), paymentRate));
    }
}
