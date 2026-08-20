package com.teenyfin.teenymoney.domain.teenyscore.service;

import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreGradeChangeVO;

/**
 * 월간 등급 변경 알림의 title·content를 한곳에서 관리한다.
 * 등급은 결제 한도와 대출·예적금 금리에 직접 연결되므로 자녀와 부모 모두에게 같은 사실을 알린다.
 */
final class TeenyScoreGradeNotificationMessages {
    private TeenyScoreGradeNotificationMessages() {
    }

    static String childTitle(TeenyScoreGradeChangeVO change) {
        return change.isUpgrade()
                ? "티니등급이 " + change.getNewGradeName() + "(으)로 올라갔어요"
                : "티니등급이 " + change.getNewGradeName() + "(으)로 내려갔어요";
    }

    static String parentTitle(TeenyScoreGradeChangeVO change) {
        return change.getChildName() + "님의 " + childTitle(change);
    }

    static String content(TeenyScoreGradeChangeVO change) {
        return change.getCurrentGradeName() + " → " + change.getNewGradeName()
                + " · 티니점수 " + change.getTeenyScore() + "점"
                + " · 결제 한도와 금리 혜택이 바뀌었어요";
    }
}
