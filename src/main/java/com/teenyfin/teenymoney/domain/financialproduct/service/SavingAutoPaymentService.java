package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class SavingAutoPaymentService {
    private final FinancialProductMapper financialProductMapper;
    private final SavingAutoPaymentProcessor processor;

    public SavingAutoPaymentService(
            FinancialProductMapper financialProductMapper,
            SavingAutoPaymentProcessor processor) {
        this.financialProductMapper = financialProductMapper;
        this.processor = processor;
    }

    public int processDuePayments(LocalDate paymentDate) {
        var enrollmentIds = financialProductMapper
                .selectDueSavingPaymentEnrollmentIds(paymentDate);
        enrollmentIds.forEach(id -> processor.process(id, paymentDate));
        return enrollmentIds.size();
    }
}
