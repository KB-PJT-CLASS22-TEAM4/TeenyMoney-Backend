package com.teenyfin.teenymoney.domain.financialproduct.scheduler;

import com.teenyfin.teenymoney.domain.financialproduct.service.FinancialProductSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FinancialProductSyncScheduler {

    private final FinancialProductSyncService financialProductSyncService;

    public FinancialProductSyncScheduler(
            FinancialProductSyncService financialProductSyncService) {
        this.financialProductSyncService = financialProductSyncService;
    }

    @Scheduled(cron = "${finlife.sync.cron:0 0 3 * * *}",
            zone = "Asia/Seoul")
    public void sync() {
        if (!financialProductSyncService.isConfigured()) {
            log.info("FINLIFE_API_KEY가 없어 금융상품 동기화를 건너뜁니다.");
            return;
        }
        try {
            int deposits = financialProductSyncService.syncDepositProducts();
            int savings = financialProductSyncService.syncSavingProducts();
            log.info("금융상품 동기화 완료: deposits={}, savings={}",
                    deposits, savings);
        } catch (RuntimeException exception) {
            log.error("금융상품 동기화 실패", exception);
        }
    }
}
