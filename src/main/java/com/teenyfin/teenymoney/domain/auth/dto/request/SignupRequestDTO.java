package com.teenyfin.teenymoney.domain.auth.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.*;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class SignupRequestDTO {

    @NotBlank(message = "이름은 필수입니다.")
    @Size(min =2, max = 7)
    @Pattern(regexp = "^[가-힣]+$", message = "이름은 한글 본명 2~7자여야 합니다.")
    private String name;

    @NotNull(message = "생년월일은 필수입니다.")
    @PastOrPresent(message = "생년월일은 미래일 수 없습니다.")
    private LocalDate birthDate;

    @NotBlank(message = "휴대폰 번호는 필수입니다.")
    @Pattern(
            regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$",
            message = "휴대폰 번호 형식이 올바르지 않습니다.")
    private String phoneNumber;

    @NotBlank(message = "인증번호는 필수입니다.")
    @Pattern(regexp = "^\\d{6}$", message = "인증번호는 숫자 6자리여야 합니다.")
    private String verificationCode;

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
    private String email;

    @NotBlank
    @Size(min = 8, max = 32)
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*(),.?\":{}|<>])\\S+$",
            message = "비밀번호는 영문, 숫자, 특수문자를 각각 포함해야 합니다."
    )
    private String password;

    @NotBlank
    private String passwordConfirm;

    @NotNull(message = "서비스 이용약관 동의 여부는 필수입니다.")
    @AssertTrue(message = "서비스 이용약관에 동의해야 합니다.")
    private Boolean serviceTermsAgreed;

    @NotNull(message = "개인정보 수집·이용 동의 여부는 필수입니다.")
    @AssertTrue(message = "개인정보 수집·이용에 동의해야 합니다.")
    private Boolean privacyAgreed;

    @NotBlank
    private String serviceTermsVersion;

    @NotBlank
    private String privacyTermsVersion;

}
