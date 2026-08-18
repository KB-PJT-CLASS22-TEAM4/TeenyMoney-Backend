package com.teenyfin.teenymoney.domain.report.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;


// 기간 내 실제로 완료된 정기 용돈 입금 한 건.
// DailySpendingVO(일자별 소비)와 나란히 Dify에 넘겨서 "용돈 받은 날 기준으로 소비가
// 몰리는지"를 LLM이 직접 대조해 찾아내게 한다 - 서버가 "n일 이내"처럼 상관관계를
// 미리 계산해서 좁혀버리지 않고, 원본 두 개를 그대로 준다.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class AllowanceCreditVO {

    private LocalDate creditedOn;
    private long amount;
}
