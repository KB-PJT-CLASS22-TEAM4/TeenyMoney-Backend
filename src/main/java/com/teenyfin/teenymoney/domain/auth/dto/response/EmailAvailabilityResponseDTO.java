package com.teenyfin.teenymoney.domain.auth.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;

@Getter
@ApiModel(description = "이메일 사용 가능 여부 응답")
public class EmailAvailabilityResponseDTO {

    @ApiModelProperty(value = "true이면 가입에 사용할 수 있음", example = "true")
    private final boolean available;

    private EmailAvailabilityResponseDTO(boolean available) {
        this.available = available;
    }

    public static EmailAvailabilityResponseDTO of(boolean available) {
        return new EmailAvailabilityResponseDTO(available);
    }
}
