package com.teenyfin.teenymoney.domain.report.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@ApiModel(description = "연령 모드")
public class AudienceResponseDTO {

    @ApiModelProperty(
            value = "리포트 대상 자녀의 연령 모드. 만 13세부터 TEEN, 그 미만은 JUNIOR. "
                    + "부모가 조회해도 자녀 기준이며, 과거 달을 봐도 현재 연령을 적용한다. "
                    + "집계값은 이 값에 따라 달라지지 않고 문구 난이도만 화면에서 달라진다.",
            example = "JUNIOR")
    private final String ageBand;
}
