package com.teenyfin.teenymoney.domain.categoryPolicy.dto.request;

import lombok.Getter;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

@Getter
public class CategoryPolicyUpdateRequestDTO {

    @NotNull(message = "id는 필수입니다.")
    Long id;

    @Pattern(regexp = "ALLOW|WATCH|BLOCK", message = "policy는 ALLOW, WATCH, BLOCK 중 하나여야 합니다.")
    String policy;
}
