package com.teenyfin.teenymoney.domain.permission.dto.request;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.List;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PermissionRequestDTO {

    @ApiModelProperty(value = "오늘만 허용을 요청할 카테고리 ID 리스트", example = "[1, 2]")
    @NotEmpty
    private List<Long> categories;

    @ApiModelProperty(value = "오늘만 허용 요청 사유", example = "오늘이 친구 생일이라 다같이 PC방에서 놀기로 했어요")
    @Size(max = 255)
    private String reason;
}
