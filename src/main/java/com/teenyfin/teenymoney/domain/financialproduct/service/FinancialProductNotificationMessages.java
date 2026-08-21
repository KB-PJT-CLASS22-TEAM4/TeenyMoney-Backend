package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductType;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;

/**
 * 예·적금·대출 알림의 title·content를 한곳에서 관리한다.
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

    // 가입 요청과 만기는 화면이 다르므로 딥링크 유형도 나눈다.
    static NotificationReferenceType maturityReferenceType(FinancialProductType type) {
        return type == FinancialProductType.DEPOSIT
                ? NotificationReferenceType.DEPOSIT_MATURITY
                : NotificationReferenceType.SAVING_MATURITY;
    }

    static NotificationReferenceType terminationReferenceType(FinancialProductType type) {
        return type == FinancialProductType.DEPOSIT
                ? NotificationReferenceType.DEPOSIT_TERMINATION
                : NotificationReferenceType.SAVING_TERMINATION;
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

    // ─────────────────────── 만기 ───────────────────────

    static String maturityChildTitle(FinancialProductType type) {
        return label(type) + " 만기 금액이 입금됐어요";
    }

    static String maturityChildContent(String productName, long principal, long interest) {
        return productName + " · 원금 " + money(principal) + " + 이자 " + money(interest);
    }

    static String maturityParentTitle(FinancialProductType type, String childName) {
        return childName + "님의 " + label(type) + " 이자를 지급했어요";
    }

    static String maturityParentContent(String productName, long interest) {
        return productName + " · 이자 " + money(interest) + "이 지갑에서 빠져나갔어요";
    }

    static String maturityInterestFailedTitle(FinancialProductType type) {
        return label(type) + " 이자를 지급하지 못했어요";
    }

    static String maturityInterestFailedContent(String productName, long interest) {
        return productName + " · 이자 " + money(interest) + " · 지갑 잔액이 부족해요. 채워주시면 다음 정산에서 자동으로 지급돼요";
    }

    // ─────────────────────── 적금 납입 ───────────────────────

    static String savingPaidTitle() {
        return "적금이 납입됐어요";
    }

    static String savingPaidContent(String productName, int installmentNo, long amount) {
        return productName + " · " + installmentNo + "회차 · " + money(amount);
    }

    /** 자유적금은 회차 개념보다 "내가 방금 넣은 금액"이 중요하므로 회차를 붙이지 않는다. */
    static String freeSavingPaidContent(String productName, long amount) {
        return productName + " · " + money(amount);
    }

    static String savingMissedTitle() {
        return "적금 자동납입에 실패했어요";
    }

    static String savingMissedContent(String productName, int installmentNo, long amount) {
        return productName + " · " + installmentNo + "회차 · 잔액이 부족해요 (" + money(amount) + ")";
    }

    // ─────────────────────── 대출 상환 ───────────────────────

    static String loanOverdueTitle() {
        return "대출 상환이 연체됐어요";
    }

    static String loanOverdueContent(
            String productName, int installmentNo, long unpaidAmount, int scoreChange) {
        return productName + " · " + installmentNo + "회차 · 미납 " + money(unpaidAmount)
                + scoreSuffix(scoreChange);
    }

    static String loanRepaidTitle() {
        return "대출을 모두 갚았어요";
    }

    static String loanRepaidContent(String productName) {
        return productName;
    }

    static String loanDefaultedTitle() {
        return "대출이 미상환으로 확정됐어요";
    }

    static String loanDefaultedContent(
            String productName, long outstandingAmount, int scoreChange) {
        return productName + " · 남은 금액 " + money(outstandingAmount) + scoreSuffix(scoreChange);
    }

    // ─────────────────────── 중도해지 ───────────────────────

    static String terminationParentTitle(FinancialProductType type, String childName) {
        return childName + "님이 " + label(type) + "을 중도해지했어요";
    }

    static String terminationParentContent(
            String productName, long principal, long interest, int scoreChange) {
        return productName + " · 원금 " + money(principal) + " + 이자 " + money(interest)
                + scoreSuffix(scoreChange);
    }

    /** 감점을 별도 알림으로 나누지 않고 원인이 된 알림의 뒤에 붙여 이유와 결과를 한 줄로 읽게 한다. */
    private static String scoreSuffix(int scoreChange) {
        if (scoreChange == 0) {
            return "";
        }
        return scoreChange < 0
                ? " · 티니점수 " + (-scoreChange) + "점 감점"
                : " · 티니점수 " + scoreChange + "점 적립";
    }

    // 적금만 월 단위 금액이라 "월"을 붙인다.
    private static String amountLine(FinancialProductType type, String productName, long amount) {
        return type == FinancialProductType.SAVING
                ? productName + " · 월 " + money(amount)
                : productName + " · " + money(amount);
    }

    /** 알림 문구에서 모든 금액을 천 단위 콤마가 포함된 원화 형식으로 표시한다. */
    private static String money(long amount) {
        return String.format("%,d원", amount);
    }

    private static String label(FinancialProductType type) {
        return switch (type) {
            case DEPOSIT -> "예금";
            case SAVING -> "적금";
            case LOAN -> "대출";
        };
    }
}
