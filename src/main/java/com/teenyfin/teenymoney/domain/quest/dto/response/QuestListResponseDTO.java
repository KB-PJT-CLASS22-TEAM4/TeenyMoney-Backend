package com.teenyfin.teenymoney.domain.quest.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
@ApiModel(description = "퀘스트 커서 목록")
public class QuestListResponseDTO {

    @ApiModelProperty(value = "최대 20개의 퀘스트")
    private final List<QuestListItemResponseDTO> items;

    @ApiModelProperty(value = "다음 페이지가 있을 때만 제공되는 커서")
    private final String nextCursor;
}
