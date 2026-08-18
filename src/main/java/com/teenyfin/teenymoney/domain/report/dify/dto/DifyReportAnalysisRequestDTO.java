package com.teenyfin.teenymoney.domain.report.dify.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.Map;


// Dify 워크플로우에 보낼 요청 Body
// 이 Dify 앱의 입력 변수는 "type"과 "data" 딱 두 개뿐이다.
// - type: 무슨 분석인지 구분하는 값. 이번 기능에선 "report" 고정.
// - data: 실제 분석 대상 데이터(이번 달+지난달 원본 데이터)를 JSON으로 직렬화한
//   "문자열" 하나. data 자체가 문자열 타입 입력 변수라, 중첩된 JSON 객체 그래프를
//   그대로 못 넣고 문자열로 한 번 감싸서 보낸다.
@Getter
public class DifyReportAnalysisRequestDTO {

    private final Map<String, String> inputs;

    @JsonProperty("response_mode")
    private final String responseMode;

    private final String user;

    public DifyReportAnalysisRequestDTO(String reportDataJson, String user) {
        this.inputs = Map.of(
                "type", "report",
                "data", reportDataJson
        );
        this.responseMode = "blocking";
        this.user = user;
    }

}
