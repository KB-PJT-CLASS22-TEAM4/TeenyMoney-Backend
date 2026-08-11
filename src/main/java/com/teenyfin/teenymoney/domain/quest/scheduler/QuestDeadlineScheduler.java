package com.teenyfin.teenymoney.domain.quest.scheduler;

import com.teenyfin.teenymoney.domain.quest.service.QuestDeadlineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

@Component
public class QuestDeadlineScheduler {

    private static final Logger log = LoggerFactory.getLogger(QuestDeadlineScheduler.class);

    private final QuestDeadlineService questDeadlineService;

    public QuestDeadlineScheduler(QuestDeadlineService questDeadlineService) {
        this.questDeadlineService = questDeadlineService;
    }

    @Scheduled(fixedDelay = 60_000)
    public void run() {
        try {
            int processed = questDeadlineService.closeExpired();
            if (processed > 0) {
                log.info("퀘스트 마감 처리 {}건", processed);
            }
        } catch (Exception e) {
            // 던지면 스케줄러가 멈춘다. 다음 주기에 다시 시도한다.
            log.error("퀘스트 마감 배치 실패", e);
        }
    }
}
