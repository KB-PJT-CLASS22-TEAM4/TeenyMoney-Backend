package com.teenyfin.teenymoney.domain.member.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@ApiModel(description = "현재 로그인한 회원 정보")
public class MemberMeResponseDTO {

    @ApiModelProperty(value = "회원 ID", example = "17")
    private final Long memberId;
    @ApiModelProperty(value = "회원 역할", allowableValues = "PARENT,CHILD", example = "CHILD")
    private final String role;
    @ApiModelProperty(value = "회원 이름", example = "김자녀")
    private final String name;
    @ApiModelProperty(value = "로그인 이메일", example = "child@example.com")
    private final String email;
    @ApiModelProperty(value = "하이픈 없이 저장된 휴대폰 번호", example = "01012345678")
    private final String phoneNumber;
    @ApiModelProperty(value = "생년월일", example = "2013-03-14")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private final LocalDate birthDate;
    @ApiModelProperty(value = "프로필 이미지 URL", example = "https://example.com/profile.png")
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
