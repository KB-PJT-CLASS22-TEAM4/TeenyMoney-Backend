package com.teenyfin.teenymoney.domain.financialproduct.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 예·적금 중도해지 견적과 실행에서 공통으로 사용하는 가입 계약 스냅샷이다. */
@Getter
@Setter
@NoArgsConstructor
public class FinancialProductTerminationVO {
    private Long enrollmentId;
    private Long childId;
    private Long parentId;
    private Long productWalletId;
    /** 예상 조회 SQL에서 함께 읽어 불필요한 지갑 FOR UPDATE를 피하기 위한 잔액 스냅샷이다. */
    private Long productWalletBalance;
    private BigDecimal appliedEarlyTerminationRate;
    private Integer termMonths;
    private String savingsType;
    private String interestCalculationType;
    private LocalDate startDate;
    private LocalDate maturityDate;
    private String status;
}
