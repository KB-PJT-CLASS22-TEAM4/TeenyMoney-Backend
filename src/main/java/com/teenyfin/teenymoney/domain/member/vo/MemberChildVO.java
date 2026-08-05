package com.teenyfin.teenymoney.domain.member.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MemberChildVO {

    private Long childId;
    private String name;
    private String email;
    // S3 오브젝트 key, 응답 전에 S3Storage.presignedUrl로 서명해야 한다.
    private String profileImageKey;
    private Integer teenyScore;
    // 지갑이 없는 자녀는 0. LEFT JOIN + COALESCE 결과
    private Long balance;
}
