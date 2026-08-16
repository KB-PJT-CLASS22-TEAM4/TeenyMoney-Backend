package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/** 대상 계약 조회와 계약별 독립 트랜잭션 실행을 담당하는 대출 상환 배치 진입점이다. */
@Slf4j
@Service
public class LoanRepaymentService {
    private final FinancialProductMapper mapper;
    private final LoanRepaymentProcessor processor;

    public LoanRepaymentService(
            FinancialProductMapper mapper, LoanRepaymentProcessor processor) {
        this.mapper = mapper;
        this.processor = processor;
    }

    public int processDueRepayments(LocalDate processingDate) {
        var ids = mapper.selectLoanRepaymentTargetIds(processingDate);
        int processed = 0;
        for (Long id : ids) {
            try {
                processor.process(id, processingDate);
                processed++;
            } catch (RuntimeException exception) {
                // 계약별 REQUIRES_NEW이므로 한 대출 실패 후에도 다른 대출은 계속 처리한다.
                log.error("대출 자동상환 실패: enrollmentId={}, date={}",
                        id, processingDate, exception);
            }
        }
        return processed;
    }
}
