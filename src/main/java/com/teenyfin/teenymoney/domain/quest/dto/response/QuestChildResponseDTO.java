package com.teenyfin.teenymoney.domain.quest.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@ApiModel(description = "퀘스트 대상 자녀")
public class QuestChildResponseDTO {

    @ApiModelProperty(value = "자녀 회원 ID", example = "2")
    private final Long childId;

    @ApiModelProperty(value = "자녀 이름", example = "김티니")
    private final String name;

    @ApiModelProperty(value = "자녀 프로필 이미지 임시 조회 주소")
    private final String profileImageUrl;
}
