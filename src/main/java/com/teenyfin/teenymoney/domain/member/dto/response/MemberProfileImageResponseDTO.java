package com.teenyfin.teenymoney.domain.member.dto.response;

import lombok.Getter;

/**
 * 프로필 이미지 변경 응답. 바뀐 것만 돌려준다.
 *
 * 값은 만료되는 서명 URL이다. 프론트는 그대로 &lt;img src&gt;에 쓸 수 있지만,
 * 로컬 스토리지 등에 캐시해 두면 만료 후 깨진다.
 */
@Getter
public class MemberProfileImageResponseDTO {

    private final String profileImageUrl;

    public MemberProfileImageResponseDTO(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }
}
