package com.teenyfin.teenymoney.domain.report.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 그 달에 끝나는 예금·적금 한 건.
 *
 * 리포트는 그 달에 일어난 일을 보는 화면이라 늘 떠 있는 상태값(현재 잔액, 가입 개수)은
 * 여기서 다루지 않는다. 그건 금융상품 화면의 몫이다. 만기와 중도 종료는 그 달에만
 * 일어나는 이벤트여서 리포트에 남는다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClosingProductVO {

    /** DEPOSIT 또는 SAVING */
    private String productType;

    private Long enrollmentId;
    private String productName;

    /** ACTIVE(만기 예정) / MATURED(만기 완료) / TERMINATED(중도 종료) */
    private String status;

    private LocalDate maturityDate;

    /** 종료가 이미 처리된 경우의 일시. 아직 만기 전이면 null */
    private LocalDate closedOn;

    /** 그 상품 지갑에 들어 있는 금액 */
    private long amount;
}
