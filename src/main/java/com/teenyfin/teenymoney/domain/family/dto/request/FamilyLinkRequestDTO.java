package com.teenyfin.teenymoney.domain.family.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import javax.validation.constraints.NotBlank;

import javax.validation.constraints.Pattern;

@Getter
@Setter
@NoArgsConstructor
@ApiModel(description = "가족 연동 코드 입력 요청")
public class FamilyLinkRequestDTO {

    @ApiModelProperty(
            value = "부모에게 전달받은 6자리 연동 코드",
            required = true,
            example = "123456"
    )
    @NotBlank(message = "연동 코드는 필수입니다.")
    @Pattern(
            regexp = "^\\d{6}$",
            message = "연동 코드는 6자리 숫자여야 합니다."
    )
    private String code;
}
