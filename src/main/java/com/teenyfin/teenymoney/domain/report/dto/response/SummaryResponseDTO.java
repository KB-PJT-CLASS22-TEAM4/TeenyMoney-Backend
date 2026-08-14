package com.teenyfin.teenymoney.domain.report.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 이번 달 한눈에 보기. 네 가지 돈의 흐름이다 (정의서 6.2).
 *
 * 데이터가 없어도 숨기지 않고 0으로 내려보낸다. 네 항목이 돈의 흐름을 배우는 구조 자체라
 * 하나가 빠지면 그림이 깨진다.
 *
 * 각 금액에 딸린 세부 값(예금/적금 분리, 원금/이자 분리, 건수)은 화면이 문장을 만들 때 쓴다.
 * 서버는 숫자만 주고 문장은 만들지 않는다.
 */
@Getter
@AllArgsConstructor
@ApiModel(description = "이번 달 한눈에 보기")
public class SummaryResponseDTO {

    @ApiModelProperty(value = "사용한 돈. 성공한 QR 결제 금액", example = "52000")
    private final long spentAmount;

    @ApiModelProperty(value = "결제 건수", example = "4")
    private final int paymentCount;

    @ApiModelProperty(value = "모은 돈. 예금 입금 + 적금 납입", example = "50000")
    private final long savedAmount;

    @ApiModelProperty(value = "그중 예금", example = "50000")
    private final long depositAmount;

    @ApiModelProperty(value = "그중 적금", example = "0")
    private final long savingAmount;

    @ApiModelProperty(value = "직접 얻은 돈. 지급이 완료된 퀘스트 보상만", example = "4000")
    private final long earnedAmount;

    @ApiModelProperty(value = "보상을 받은 횟수", example = "1")
    private final int earnedCount;

    @ApiModelProperty(value = "갚은 돈. 실제 납부한 원금 + 이자", example = "11000")
    private final long repaidAmount;

    @ApiModelProperty(value = "그중 원금", example = "10000")
    private final long repaidPrincipal;

    @ApiModelProperty(value = "그중 이자", example = "1000")
    private final long repaidInterest;

    @ApiModelProperty(value = "상환 완료 회차 수", example = "1")
    private final int repaidCount;
}
