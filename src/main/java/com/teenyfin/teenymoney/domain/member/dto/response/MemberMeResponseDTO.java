package com.teenyfin.teenymoney.domain.member.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import lombok.Getter;

import java.time.LocalDate;

@Getter
public class MemberMeResponseDTO {

    private final Long memberId;
    private final String role;
    private final String name;
    private final String email;
    private final String phoneNumber;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private final LocalDate birthDate;
    /** 만료되는 서명 URL. MemberVO가 들고 있는 key를 그대로 넣으면 안 된다. */
    private final String profileImageUrl;

    private MemberMeResponseDTO(MemberVO member, String profileImageUrl) {
        this.memberId = member.getId();
        this.role = member.getRole();
        this.name = member.getName();
        this.email = member.getEmail();
        this.phoneNumber = member.getPhoneNumber();
        this.birthDate = member.getBirthDate();
        this.profileImageUrl = profileImageUrl;
    }

    /**
     * profileImageUrl은 S3Storage.presignedUrl()이 만든 값이어야 한다.
     *
     * MemberVO 하나만 받는 팩토리를 두지 않는 이유가 이것이다. 그런 게 있으면
     * 서명을 빼먹은 호출이 컴파일도 되고 테스트도 통과하며, 브라우저가 403을
     * 받을 때까지 아무도 모른다.
     */
    public static MemberMeResponseDTO of(MemberVO member, String profileImageUrl) {
        return new MemberMeResponseDTO(member, profileImageUrl);
    }
}
