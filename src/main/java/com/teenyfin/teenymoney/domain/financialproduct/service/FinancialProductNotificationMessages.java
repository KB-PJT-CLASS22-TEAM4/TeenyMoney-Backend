package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductType;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;

/**
 * 예·적금·대출 가입 요청/승인/거절 알림의 title·content를 한곳에서 관리한다.
 * "알림 딥링크 인벤토리" 문서에 정의된 문구를 그대로 옮긴 것이라, 문구를 바꿀 때는 문서도 같이 고쳐야 한다.
 */
final class FinancialProductNotificationMessages {
    private FinancialProductNotificationMessages() {
    }

    static NotificationReferenceType referenceType(FinancialProductType type) {
        return switch (type) {
            case DEPOSIT -> NotificationReferenceType.DEPOSIT_ENROLLMENT;
            case SAVING -> NotificationReferenceType.SAVING_ENROLLMENT;
            case LOAN -> NotificationReferenceType.LOAN_ENROLLMENT;
        };
    }

    static String requestTitle(FinancialProductType type, String childName) {
        return childName + "님이 " + label(type) + " 가입을 요청했어요";
    }

    static String requestContent(FinancialProductType type, String productName, long amount) {
        return amountLine(type, productName, amount);
    }

    // 대출만 "가입"이 아니라 "신청"으로 부른다 — 인벤토리 문서 문구 그대로.
    static String approvedTitle(FinancialProductType type) {
        return type == FinancialProductType.LOAN
                ? "대출 신청이 승인됐어요"
                : label(type) + " 가입이 승인됐어요";
    }

    static String approvedContent(FinancialProductType type, String productName, long amount) {
        return amountLine(type, productName, amount);
    }

    static String rejectedTitle(FinancialProductType type) {
        return type == FinancialProductType.LOAN
                ? "대출 신청이 거절됐어요"
                : label(type) + " 가입이 거절됐어요";
    }

    static String rejectedContent(String productName) {
        return productName;
    }

    // 적금만 월 단위 금액이라 "월"을 붙인다.
    private static String amountLine(FinancialProductType type, String productName, long amount) {
        return type == FinancialProductType.SAVING
                ? productName + " · 월 " + amount + "원"
                : productName + " · " + amount + "원";
    }

    private static String label(FinancialProductType type) {
        return switch (type) {
            case DEPOSIT -> "예금";
            case SAVING -> "적금";
            case LOAN -> "대출";
        };
    }
}
