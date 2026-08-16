package com.teenyfin.teenymoney.domain.quest.vo;

/**
 * 자녀가 퀘스트를 거절할 때 고르는 사유.
 *
 * label 은 부모에게 가는 알림에서 상세 사유가 없을 때 대신 보여 준다.
 * 저장·응답은 그대로 이름(name())을 쓰므로 API 계약은 바뀌지 않는다.
 */
public enum DeclineReasonCode {
    NOT_ENOUGH_TIME("시간이 부족해요"),
    TOO_DIFFICULT("너무 어려워요"),
    REWARD_NOT_ENOUGH("보상이 아쉬워요"),
    HARD_TO_VERIFY("인증하기 어려워요"),
    CANNOT_DO_NOW("지금은 할 수 없어요"),
    OTHER("기타");

    private final String label;

    DeclineReasonCode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
