package com.teenyfin.teenymoney.domain.notification.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
@ApiModel(description = "알림 커서 목록")
public class NotificationListResponseDTO {

    @ApiModelProperty(value = "최대 10개의 알림")
    private final List<NotificationResponseDTO> notifications;

    @ApiModelProperty(value = "다음 페이지가 있을 때만 제공되는 커서")
    private final String nextCursor;
}
