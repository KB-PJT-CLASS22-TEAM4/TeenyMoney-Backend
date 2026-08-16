package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.YearMonth;

@Slf4j
@Service
public class FreeSavingMonthlyScoreService {
    private final FinancialProductMapper financialProductMapper;
    private final FreeSavingMonthlyScoreProcessor processor;

    public FreeSavingMonthlyScoreService(
            FinancialProductMapper financialProductMapper,
            FreeSavingMonthlyScoreProcessor processor) {
        this.financialProductMapper = financialProductMapper;
        this.processor = processor;
    }

    public int processMonthlyScores(YearMonth targetMonth) {
        // 처리 대상은 먼저 ID만 조회하고, 계약별 잠금과 점수 반영은 Processor의 독립 트랜잭션에 맡긴다.
        var ids = financialProductMapper.selectFreeSavingMonthlyTargetIds(
                targetMonth.atDay(1), targetMonth.plusMonths(1).atDay(1));
        int processed = 0;
        for (Long id : ids) {
            try {
                processor.process(id, targetMonth);
                processed++;
            } catch (RuntimeException exception) {
                log.error("자유적금 월별 점수 처리 실패: enrollmentId={}, month={}",
                        id, targetMonth, exception);
            }
        }
        return processed;
    }
}
