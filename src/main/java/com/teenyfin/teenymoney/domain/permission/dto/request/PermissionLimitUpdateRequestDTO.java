package com.teenyfin.teenymoney.domain.permission.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Getter
@NoArgsConstructor
@ApiModel(description = "자녀의 월간 오늘만 허용 가능 일수 변경 요청")
public class PermissionLimitUpdateRequestDTO {
    @NotNull(message = "월간 오늘만 허용 가능 일수를 입력해주세요.")
    @Min(value = 0, message = "월간 오늘만 허용 가능 일수는 0 이상이어야 합니다.")
    @Max(value = 31, message = "월간 오늘만 허용 가능 일수는 31 이하여야 합니다.")
    @ApiModelProperty(value = "한 달에 오늘만 허용을 요청할 수 있는 날짜 수", example = "3")
    private Integer monthlyAllowedDays;

    public PermissionLimitUpdateRequestDTO(Integer monthlyAllowedDays) {
        this.monthlyAllowedDays = monthlyAllowedDays;
    }
}
