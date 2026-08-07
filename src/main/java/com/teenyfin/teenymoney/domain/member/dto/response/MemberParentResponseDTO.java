package com.teenyfin.teenymoney.domain.member.dto.response;

import com.teenyfin.teenymoney.domain.member.vo.MemberParentVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;

/**
 * 자녀에게 내려가는 부모 정보.
 *
 * MemberChildResponseDTO와 대칭처럼 보이지만 balance·teenyScore·email이 없다.
 * 감독하는 쪽이 감독받는 쪽의 재무 정보를 보는 것이고 그 역은 아니다.
 */
@Getter
@ApiModel(description = "자녀와 연동된 부모 정보")
public class MemberParentResponseDTO {

    @ApiModelProperty(value = "부모 회원 ID", example = "1")
    private final Long parentId;
    @ApiModelProperty(value = "부모 이름", example = "김부모")
    private final String name;
    /** 만료되는 서명 URL. MemberParentVO가 들고 있는 key를 그대로 넣으면 안 된다. */
    @ApiModelProperty(value = "프로필 이미지 URL", example = "https://example.com/profile.png")
    private final String profileImageUrl;

    private MemberParentResponseDTO(MemberParentVO parent, String profileImageUrl) {
        this.parentId = parent.getParentId();
        this.name = parent.getName();
        this.profileImageUrl = profileImageUrl;
    }

    public static MemberParentResponseDTO of(MemberParentVO parent, String profileImageUrl) {
        return new MemberParentResponseDTO(parent, profileImageUrl);
    }
}