package com.teenyfin.teenymoney.domain.quest.dto.request;

import com.teenyfin.teenymoney.domain.quest.vo.AfterDeadlineAction;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Size;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "부모의 퀘스트 인증 반려 요청")
public class QuestRejectRequestDTO {

    @Size(max = 500, message = "반려 사유는 500자 이하여야 합니다.")
    @ApiModelProperty(
            value = "선택적 반려 사유. 생략하거나 공백만 입력하면 사유 없음으로 처리합니다.",
            example = "인증 사진에서 완료 여부를 확인할 수 없어요.")
    private String reason;

    @ApiModelProperty(value = "기한 후 처리 방법. 기한이 지났고 재시도 기회가 남았을 때 필수",
            example = "EXTEND")
    private AfterDeadlineAction afterDeadlineAction;

    @ApiModelProperty(value = "연장 기한. afterDeadlineAction이 EXTEND일 때 필수",
            example = "2026-08-13T20:00:00")
    private LocalDateTime extendedDeadline;
}
