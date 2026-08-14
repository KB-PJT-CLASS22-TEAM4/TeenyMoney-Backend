package com.teenyfin.teenymoney.domain.report.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * 이달의 티니점수 변화 (정의서 6.6).
 *
 * 이 화면은 '선택한 달에 실제 발생한 변화'만 다룬다. 현재 점수와 등급은 티니점수 화면의 몫이다.
 * 기간 내 이력이 하나도 없으면 이 객체 자체가 null 이고, 화면은 중립 한 줄만 보여준다.
 *
 * 대표 원인은 저장된 event_code 와 description 을 그대로 쓴다. 증명되지 않은 원인을
 * 서버가 지어내지 않는다.
 */
@Getter
@AllArgsConstructor
@ApiModel(description = "이달의 티니점수 변화")
public class MonthlyScoreResponseDTO {

    @ApiModelProperty(value = "기간 내 순변동", example = "-6")
    private final int netChange;

    @ApiModelProperty(value = "점수가 오른 이벤트 수", example = "0")
    private final int increaseEventCount;

    @ApiModelProperty(value = "점수가 내린 이벤트 수", example = "2")
    private final int decreaseEventCount;

    @ApiModelProperty(value = "대표 원인. 변동 폭이 큰 순서로 최대 2개")
    private final List<MonthlyScoreReasonResponseDTO> topReasons;
}
