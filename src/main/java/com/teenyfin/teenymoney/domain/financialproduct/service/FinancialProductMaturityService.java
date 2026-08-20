package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Slf4j
@Service
public class FinancialProductMaturityService {
    private final FinancialProductMapper financialProductMapper;
    private final FinancialProductMaturityProcessor processor;

    public FinancialProductMaturityService(
            FinancialProductMapper financialProductMapper,
            FinancialProductMaturityProcessor processor) {
        this.financialProductMapper = financialProductMapper;
        this.processor = processor;
    }

    public int processMaturities(LocalDate processingDate) {
        // 대상 전체를 한 트랜잭션으로 묶지 않아 한 계약 실패가 나머지 만기 정산을 막지 않게 한다.
        int processed = 0;
        for (Long id : financialProductMapper.selectDueDepositMaturityIds(processingDate)) {
            try {
                processor.processDeposit(id, processingDate);
                processed++;
            } catch (FinancialProductInterestPaymentFailedException failure) {
                log.error("예금 만기 정산 실패(부모 잔액 부족): enrollmentId={}", id, failure);
                processor.notifyInterestPaymentFailed(failure);
            } catch (RuntimeException exception) {
                log.error("예금 만기 정산 실패: enrollmentId={}", id, exception);
            }
        }
        for (Long id : financialProductMapper.selectDueSavingMaturityIds(processingDate)) {
            try {
                processor.processSaving(id, processingDate);
                processed++;
            } catch (FinancialProductInterestPaymentFailedException failure) {
                log.error("적금 만기 정산 실패(부모 잔액 부족): enrollmentId={}", id, failure);
                processor.notifyInterestPaymentFailed(failure);
            } catch (RuntimeException exception) {
                log.error("적금 만기 정산 실패: enrollmentId={}", id, exception);
            }
        }
        return processed;
    }
}
