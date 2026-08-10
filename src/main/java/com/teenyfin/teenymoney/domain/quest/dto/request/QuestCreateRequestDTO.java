package com.teenyfin.teenymoney.domain.quest.dto.request;

import com.teenyfin.teenymoney.domain.quest.vo.VerificationRequirement;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "퀘스트 일괄 생성 요청")
public class QuestCreateRequestDTO {

    @Valid
    @NotEmpty(message = "퀘스트를 받을 자녀를 한 명 이상 선택해 주세요.")
    @ApiModelProperty(value = "퀘스트를 받을 자녀 ID 목록", required = true, example = "[2, 3]")
    private List<@NotNull @Positive Long> childIds;

    @NotBlank(message = "제목을 입력해 주세요.")
    @Size(max = 50, message = "제목은 50자 이하여야 합니다.")
    @ApiModelProperty(value = "제목", required = true, example = "방 청소하기")
    private String title;

    @NotBlank(message = "내용을 입력해 주세요.")
    @Size(max = 500, message = "내용은 500자 이하여야 합니다.")
    @ApiModelProperty(value = "상세 내용", required = true, example = "책상과 바닥을 정리해 주세요.")
    private String content;

    @NotNull(message = "기한을 입력해 주세요.")
    @ApiModelProperty(value = "서버 시간 기준 수행 기한", required = true,
            example = "2026-08-11T20:00:00")
    private LocalDateTime deadline;

    @NotNull(message = "현금 보상 금액을 입력해 주세요.")
    @PositiveOrZero(message = "현금 보상은 0원 이상이어야 합니다.")
    @ApiModelProperty(value = "자녀 한 명당 현금 보상", required = true, example = "1000")
    private Long rewardAmount;

    @NotNull(message = "티니점수 적용 여부를 선택해 주세요.")
    @ApiModelProperty(value = "티니점수 적용 여부", required = true, example = "true")
    private Boolean teenyScoreEnabled;

    @NotNull(message = "인증 방식을 선택해 주세요.")
    @ApiModelProperty(value = "인증 방식", required = true, example = "PHOTO_REQUIRED")
    private VerificationRequirement verificationRequirement;
}
