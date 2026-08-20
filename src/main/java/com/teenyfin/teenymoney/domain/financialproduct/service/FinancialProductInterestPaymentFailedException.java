package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductType;
import lombok.Getter;

/**
 * 만기 정산 중 부모 지갑 잔액 부족으로 이자를 지급하지 못해 정산 전체가 롤백됐을 때 던진다.
 * 정산 트랜잭션 밖에서 부모에게 알림을 보내는 데 필요한 최소 정보만 담는다.
 */
@Getter
class FinancialProductInterestPaymentFailedException extends RuntimeException {
    private final Long enrollmentId;
    private final Long childId;
    private final Long parentId;
    private final String productName;
    private final FinancialProductType type;
    private final long interest;

    FinancialProductInterestPaymentFailedException(
            Long enrollmentId, Long childId, Long parentId, String productName,
            FinancialProductType type, long interest, Throwable cause) {
        super("만기 이자 지급 실패: enrollmentId=" + enrollmentId, cause);
        this.enrollmentId = enrollmentId;
        this.childId = childId;
        this.parentId = parentId;
        this.productName = productName;
        this.type = type;
        this.interest = interest;
    }
}
