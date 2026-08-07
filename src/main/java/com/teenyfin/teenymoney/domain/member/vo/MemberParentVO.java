package com.teenyfin.teenymoney.domain.member.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MemberParentVO {
    private Long parentId;
    private String name;
    // S3 오브젝트 key, 응답 전에 S3Storage.presignedUrl로 서명해야 한다.
    private String profileImageKey;
}
