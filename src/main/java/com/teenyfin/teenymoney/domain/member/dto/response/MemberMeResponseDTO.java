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
    /** 만료되는 서명 URL. MemberVO가 들고 있는 key를 그대로 넣으면 안 된다. */
    @ApiModelProperty(value = "프로필 이미지 URL", example = "https://example.com/profile.png")
    private final String profileImageUrl;

    // 결제 비밀번호 "등록 여부"만 내려준다 - 값 자체는 절대 안 내려줌.
    // 프론트는 이 값이 false일 때만 결제 비밀번호 설정 화면으로 분기하는 용도로 쓴다.
    // 실제 보안 검증은 여전히 ChargeService.createPendingCharge()가 매 충전마다
    // checkPaymentPassword()로 따로 하므로, 이 값이 stale하거나 프론트가 무시해도
    // 서버가 최종적으로 막아준다.
    @ApiModelProperty(value = "결제 비밀번호 등록 여부", example = "true or false")
    private final boolean hasPaymentPassword;

    private MemberMeResponseDTO(MemberVO member, String profileImageUrl) {
        this.memberId = member.getId();
        this.role = member.getRole();
        this.name = member.getName();
        this.email = member.getEmail();
        this.phoneNumber = member.getPhoneNumber();
        this.birthDate = member.getBirthDate();
        this.profileImageUrl = profileImageUrl;
        this.hasPaymentPassword = member.getPaymentPassword() != null;
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
