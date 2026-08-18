package com.teenyfin.teenymoney.domain.report.dify.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DifyReportAnalysisResponseDTO {

    private Data data;
    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {

        // succeeded/failed/running/stopped 등 - 얘가 failed인데 outputs가 비어있는
        // 경우까지 extractAnalysisText()의 null 체크 하나로 같이 걸러진다.
        private String status;


        private Map<String, Object> outputs;
    }

    // outputs.text를 문자열로 꺼냄. 없거나 이상하면 null
    // null을 "응답이 올바르지 않다"는 신호 해석.

    public String extractAnalysisText() {
        if (data == null || data.outputs == null) {
            return null;
        }
        Object value = data.outputs.get("text");
        return value == null ? null : value.toString();
    }


}
