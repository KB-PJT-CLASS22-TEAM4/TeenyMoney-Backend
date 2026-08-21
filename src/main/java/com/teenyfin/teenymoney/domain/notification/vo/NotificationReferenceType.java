package com.teenyfin.teenymoney.domain.notification.vo;

public enum NotificationReferenceType {
    QUEST, TODAY_PERMISSION, CONNECTION, TRANSFER, PAYMENT, CHARGE, DEPOSIT_ENROLLMENT, SAVING_ENROLLMENT, LOAN_ENROLLMENT,
    // 가입 이후 계약 생애주기에서 발생하는 알림. referenceId는 모두 가입 ID다.
    DEPOSIT_MATURITY, SAVING_MATURITY, SAVING_PAYMENT, LOAN_REPAYMENT,
    DEPOSIT_TERMINATION, SAVING_TERMINATION,
    // 매월 확정되는 티니등급 변경. referenceId는 자녀 회원 ID다.
    TEENY_SCORE_GRADE,
    // 부모가 카테고리 정책(허용/주의/차단)을 변경. 한 번에 여러 카테고리가 바뀔 수 있어 referenceId는 없다.
    CATEGORY_POLICY
}
