package com.teenyfin.teenymoney.domain.quest.dto.request;

import com.teenyfin.teenymoney.domain.quest.vo.DeclineReasonCode;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "자녀의 퀘스트 거절 요청")
public class QuestDeclineRequestDTO {

    @NotNull(message = "거절 사유를 선택해 주세요.")
    @ApiModelProperty(value = "거절 사유 코드", required = true, example = "NOT_ENOUGH_TIME")
    private DeclineReasonCode reasonCode;

    @Size(max = 500, message = "상세 사유는 500자 이하여야 합니다.")
    @ApiModelProperty(value = "상세 사유. reasonCode가 OTHER이면 필수입니다.", example = "학원 일정이 겹쳐요")
    private String reasonDetail;
}