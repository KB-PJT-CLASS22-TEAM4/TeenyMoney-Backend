package com.teenyfin.teenymoney.domain.member.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class MemberVO {

    private Long id;
    private String role;
    private String name;
    private LocalDate birthDate;
    private String phoneNumber;
    private String email;
    private String password;
    /** S3 오브젝트 key다. URL이 아니다. 응답에 내보내기 전에 S3Storage.presignedUrl()로 서명해야 한다. */
    private String profileImageKey;
    private String status;
    private String customerKey;
    // 값(bcrypt 해시) 자체는 DTO 밖으로 절대 안 나감 - MemberMeResponseDTO가
    // null 여부만 뽑아서 hasPaymentPassword로 변환한 뒤 이 필드는 버린다.
    private String paymentPassword;
}
