package com.teenyfin.teenymoney.domain.teenyscore.service;

import com.teenyfin.teenymoney.domain.notification.service.NotificationService;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;
import com.teenyfin.teenymoney.domain.teenyscore.exception.TeenyScoreErrorCode;
import com.teenyfin.teenymoney.domain.teenyscore.mapper.TeenyScoreMapper;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreGradeChangeVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** 실시간 점수와 분리된 월간 적용 등급을 현재 점수 기준으로 갱신한다. */
@Service
public class TeenyScoreGradeService {

    private final TeenyScoreMapper teenyScoreMapper;
    private final Clock clock;
    private final NotificationService notificationService;

    public TeenyScoreGradeService(
            TeenyScoreMapper teenyScoreMapper,
            Clock clock,
            NotificationService notificationService) {
        this.teenyScoreMapper = teenyScoreMapper;
        this.clock = clock;
        this.notificationService = notificationService;
    }

    @Transactional
    public int applyMonthlyGrades() {
        // 일괄 UPDATE는 변경된 자녀를 알려주지 않으므로 갱신 전에 변경 대상을 먼저 확보한다.
        List<TeenyScoreGradeChangeVO> changes = teenyScoreMapper.selectPendingGradeChanges();
        int updatedCount = teenyScoreMapper.updateAllActiveChildGrades(
                LocalDateTime.now(clock));
        changes.forEach(this::notifyGradeChanged);
        return updatedCount;
    }

    /** 등급은 결제 한도와 금리 혜택을 바꾸므로 자녀와 보호자 모두에게 알린다. */
    private void notifyGradeChanged(TeenyScoreGradeChangeVO change) {
        String content = TeenyScoreGradeNotificationMessages.content(change);
        notificationService.createNotification(
                change.getChildId(),
                TeenyScoreGradeNotificationMessages.childTitle(change),
                content, NotificationReferenceType.TEENY_SCORE_GRADE,
                change.getChildId(), true);

        // 연결된 보호자가 없는 자녀도 있으므로 부모 알림은 연결이 있을 때만 보낸다.
        if (change.getParentId() == null) {
            return;
        }
        notificationService.createNotification(
                change.getParentId(),
                TeenyScoreGradeNotificationMessages.parentTitle(change),
                content, NotificationReferenceType.TEENY_SCORE_GRADE,
                change.getChildId(), true);
    }

    /** 신규 자녀의 저장된 초기 점수를 기준으로 최초 적용 등급을 부여한다. */
    @Transactional
    public void initializeGrade(Long childId) {
        int updatedCount = teenyScoreMapper.initializeAppliedGrade(
                childId, LocalDateTime.now(clock));
        if (updatedCount != 1) {
            throw new BusinessException(
                    TeenyScoreErrorCode.TEENY_SCORE_CHILD_NOT_FOUND);
        }
    }
}
