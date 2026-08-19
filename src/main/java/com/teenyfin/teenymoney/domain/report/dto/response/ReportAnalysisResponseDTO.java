package com.teenyfin.teenymoney.domain.report.dto.response;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@ApiModel(description = "이번 달·지난달 금융 습관 AI 분석")
public class ReportAnalysisResponseDTO {

    @ApiModelProperty(value = "LLM이 생성한 서술형 분석, 조언 텍스트", example = "이번 달엔 용돈을 받은 날 크게 쓰는 습관이 보였어요.. 등")
    private final String analysis;
}
