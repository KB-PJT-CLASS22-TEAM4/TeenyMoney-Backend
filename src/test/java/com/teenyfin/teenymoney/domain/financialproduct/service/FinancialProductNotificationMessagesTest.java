package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FinancialProductNotificationMessagesTest {

    @Test
    @DisplayName("금융상품 알림의 모든 금액을 천 단위 콤마로 표시한다")
    void 금융상품_알림_금액에_천_단위_콤마를_표시한다() {
        assertEquals("예금상품 · 100,000원",
                FinancialProductNotificationMessages.requestContent(
                        FinancialProductType.DEPOSIT, "예금상품", 100_000L));
        assertEquals("적금상품 · 월 10,000원",
                FinancialProductNotificationMessages.approvedContent(
                        FinancialProductType.SAVING, "적금상품", 10_000L));
        assertEquals("예금상품 · 원금 100,000원 + 이자 2,050원",
                FinancialProductNotificationMessages.maturityChildContent(
                        "예금상품", 100_000L, 2_050L));
        assertEquals("예금상품 · 이자 2,050원이 지갑에서 빠져나갔어요",
                FinancialProductNotificationMessages.maturityParentContent(
                        "예금상품", 2_050L));
        assertEquals("예금상품 · 이자 2,050원 · 지갑 잔액이 부족해요. "
                        + "채워주시면 다음 정산에서 자동으로 지급돼요",
                FinancialProductNotificationMessages.maturityInterestFailedContent(
                        "예금상품", 2_050L));
        assertEquals("적금상품 · 3회차 · 10,000원",
                FinancialProductNotificationMessages.savingPaidContent(
                        "적금상품", 3, 10_000L));
        assertEquals("자유적금 · 10,000원",
                FinancialProductNotificationMessages.freeSavingPaidContent(
                        "자유적금", 10_000L));
        assertEquals("적금상품 · 3회차 · 잔액이 부족해요 (10,000원)",
                FinancialProductNotificationMessages.savingMissedContent(
                        "적금상품", 3, 10_000L));
        assertEquals("대출상품 · 2회차 · 미납 12,345원 · 티니점수 2점 감점",
                FinancialProductNotificationMessages.loanOverdueContent(
                        "대출상품", 2, 12_345L, -2));
        assertEquals("대출상품 · 남은 금액 50,000원 · 티니점수 20점 감점",
                FinancialProductNotificationMessages.loanDefaultedContent(
                        "대출상품", 50_000L, -20));
        assertEquals("예금상품 · 원금 100,000원 + 이자 500원 · 티니점수 3점 감점",
                FinancialProductNotificationMessages.terminationParentContent(
                        "예금상품", 100_000L, 500L, -3));
        assertEquals("연속 만기 보너스를 받았어요",
                FinancialProductNotificationMessages.consecutiveMaturityTitle());
        assertEquals("6개월 이상 예·적금 상품을 2회 연속 만기해 티니점수 10점을 받았어요.",
                FinancialProductNotificationMessages.consecutiveMaturityContent());
        assertEquals("연속 중도해지로 점수가 차감됐어요",
                FinancialProductNotificationMessages.repeatedEarlyTerminationTitle());
        assertEquals("예·적금 상품을 3회 연속 중도해지해 티니점수 8점이 차감됐어요.",
                FinancialProductNotificationMessages.repeatedEarlyTerminationContent());
    }
}
