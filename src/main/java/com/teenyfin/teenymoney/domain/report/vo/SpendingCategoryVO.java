package com.teenyfin.teenymoney.domain.report.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 카테고리 × 적용정책 단위의 집계 한 줄.
 *
 * appliedPolicy를 GROUP BY 축에 넣어두면 같은 결과를 두 가지로 접을 수 있다.
 * 카테고리 목록은 정책 무관 합산, 주의 업종 목록은 WATCH 행만 모은다.
 * 정책이 바뀌어 같은 카테고리의 과거 결제가 ALLOW와 WATCH 양쪽에 걸려 있어도 정확하다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpendingCategoryVO {

    private Long categoryId;
    private String categoryName;
    private String appliedPolicy;
    private long amount;
    private int paymentCount;
}
