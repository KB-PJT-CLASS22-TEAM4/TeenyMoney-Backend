package com.teenyfin.teenymoney.domain.member.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.teenyfin.teenymoney.domain.member.vo.MemberChildVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@ApiModel(description = "부모와 연동된 자녀 정보")
public class MemberChildResponseDTO {

    @ApiModelProperty(value = "자녀 회원 ID", example = "2")
    private final Long childId;
    @ApiModelProperty(value = "자녀 이름", example = "김첫째")
    private final String name;
    @ApiModelProperty(value = "자녀 로그인 이메일", example = "child1@test.com")
    private final String email;
    /** 만료되는 서명 URL. MemberChildVO가 들고 있는 key를 그대로 넣으면 안 된다. */
    @ApiModelProperty(value = "프로필 이미지 URL", example = "https://example.com/profile.png")
    private final String profileImageUrl;
    @ApiModelProperty(value = "티니점수 0~1000", example = "610")
    private final Integer teenyScore;
    @ApiModelProperty(value = "자녀 지갑 잔액(원). 지갑이 없으면 0", example = "96500")
    private final Long balance;
    @ApiModelProperty(value = "생년월일", example = "2013-03-14")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private final LocalDate birthDate;

    // 생성자·팩토리는 그대로
    private MemberChildResponseDTO(MemberChildVO child, String profileImageUrl) {
        this.childId = child.getChildId();
        this.name = child.getName();
        this.email = child.getEmail();
        this.profileImageUrl = profileImageUrl;
        this.teenyScore = child.getTeenyScore();
        this.balance = child.getBalance();
        this.birthDate = child.getBirthDate();
    }

    public static MemberChildResponseDTO of(MemberChildVO child, String profileImageUrl) {
        return new MemberChildResponseDTO(child, profileImageUrl);
    }
}