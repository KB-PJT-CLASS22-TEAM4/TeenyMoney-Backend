package com.teenyfin.teenymoney.domain.report.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

/**
 * 금융 습관 카드 한 장 (정의서 7.2).
 *
 * 서버는 완성된 문장을 만들지 않는다. 안정적인 insightCode 와 수치만 주고, 연령별 문구는
 * 화면의 문구 사전이 조립한다. 그래야 문구 한 줄 고치는 데 배포가 필요 없고,
 * JUNIOR 와 TEEN 이 같은 사실을 다른 난이도로 말할 수 있다.
 */
@Getter
@AllArgsConstructor
@ApiModel(description = "금융 습관 인사이트")
public class InsightResponseDTO {

    @ApiModelProperty(
            value = "문구 사전의 키가 되는 고정 코드. "
                    + "SPENDING_TOP_CATEGORY / PERMISSION_STATUS_SUMMARY / "
                    + "SAVING_PAYMENT_PROGRESS / LOAN_REPAYMENT_PROGRESS / "
                    + "LOAN_OVERDUE / QUEST_COMPLETED",
            example = "SAVING_PAYMENT_PROGRESS")
    private final String insightCode;

    @ApiModelProperty(
            value = "문구에 끼워 넣을 수치. 코드마다 키가 다르다",
            example = "{\"paidCount\":3,\"totalCount\":6,\"progressRate\":50}")
    private final Map<String, Object> metrics;

    @ApiModelProperty(
            value = "카드를 눌렀을 때 이동할 화면. 없으면 null",
            example = "/child/finance/enrollments/21")
    private final String deepLink;
}
