package com.teenyfin.teenymoney.domain.allowance.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotNull;

@Getter
@Setter
@NoArgsConstructor
@ApiModel(description = "정기 용돈 스케줄 활성화/비활성화 토글 요청")
public class AllowanceScheduleStatusRequestDTO {

    @ApiModelProperty(value = "true면 활성화, false면 비활성화", required = true, example = "true")
    // 타입이 boolean이 아니라 Boolean(래퍼 타입)인 이유: 클라이언트가 이 필드를 아예 안 보내면
    // primitive boolean은 자동으로 false가 채워져버려서 "false를 보낸 건지 안 보낸 건지" 구분이
    // 안 됨. Boolean(객체 타입)이면 안 보낸 경우 null이 되고, 그걸 @NotNull이 잡아서 400을 던짐.
    @NotNull(message = "isActive는 필수입니다.")
    private Boolean isActive;
}
