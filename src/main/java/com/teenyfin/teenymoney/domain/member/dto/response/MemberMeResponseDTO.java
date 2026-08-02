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
    private final String profileImageUrl;

    private MemberMeResponseDTO(MemberVO member) {
        this.memberId = member.getId();
        this.role = member.getRole();
        this.name = member.getName();
        this.email = member.getEmail();
        this.phoneNumber = member.getPhoneNumber();
        this.birthDate = member.getBirthDate();
        this.profileImageUrl = member.getProfileImageUrl();
    }

    public static MemberMeResponseDTO of(MemberVO member) {
        return new MemberMeResponseDTO(member);
    }
}
