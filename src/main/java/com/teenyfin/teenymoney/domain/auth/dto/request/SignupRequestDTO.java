package com.teenyfin.teenymoney.domain.auth.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.*;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@ApiModel(description = "회원가입 요청")
public class SignupRequestDTO {

    @ApiModelProperty(value = "한글 본명 2~7자", required = true, example = "김자녀")
    @NotBlank(message = "이름은 필수입니다.")
    @Size(min =2, max = 7)
    @Pattern(regexp = "^[가-힣]+$", message = "이름은 한글 본명 2~7자여야 합니다.")
    private String name;

    @ApiModelProperty(value = "생년월일(YYYY-MM-DD)", required = true, example = "2013-03-14")
    @NotNull(message = "생년월일은 필수입니다.")
    @PastOrPresent(message = "생년월일은 미래일 수 없습니다.")
    private LocalDate birthDate;

    @ApiModelProperty(value = "가입자 휴대폰 번호", required = true, example = "010-1234-5678")
    @NotBlank(message = "휴대폰 번호는 필수입니다.")
    @Pattern(
            regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$",
            message = "휴대폰 번호 형식이 올바르지 않습니다.")
    private String phoneNumber;

    @ApiModelProperty(value = "가입자 휴대폰으로 받은 6자리 인증번호", required = true, example = "123456")
    @NotBlank(message = "인증번호는 필수입니다.")
    @Pattern(regexp = "^\\d{6}$", message = "인증번호는 숫자 6자리여야 합니다.")
    private String verificationCode;

    @ApiModelProperty(value = "로그인에 사용할 이메일", required = true, example = "child@example.com")
    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
    private String email;

    @ApiModelProperty(value = "영문·숫자·특수문자를 포함한 8~32자 비밀번호", required = true, example = "Abcd!2345")
    @NotBlank
    @Size(min = 8, max = 32)
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*(),.?\":{}|<>])\\S+$",
            message = "비밀번호는 영문, 숫자, 특수문자를 각각 포함해야 합니다."
    )
    private String password;

    @ApiModelProperty(value = "비밀번호 확인", required = true, example = "Abcd!2345")
    @NotBlank
    private String passwordConfirm;

    @ApiModelProperty(value = "서비스 이용약관 동의 여부", required = true, example = "true")
    @NotNull(message = "서비스 이용약관 동의 여부는 필수입니다.")
    @AssertTrue(message = "서비스 이용약관에 동의해야 합니다.")
    private Boolean serviceTermsAgreed;

    @ApiModelProperty(value = "개인정보 수집·이용 동의 여부", required = true, example = "true")
    @NotNull(message = "개인정보 수집·이용 동의 여부는 필수입니다.")
    @AssertTrue(message = "개인정보 수집·이용에 동의해야 합니다.")
    private Boolean privacyAgreed;

    @ApiModelProperty(value = "서비스 이용약관 버전", required = true, example = "1.0")
    @NotBlank
    private String serviceTermsVersion;

    @ApiModelProperty(value = "개인정보 약관 버전", required = true, example = "1.0")
    @NotBlank
    private String privacyTermsVersion;

    @ApiModelProperty(
            value = "만 14세 미만 가입자에게 필수인 일회용 법정대리인 동의 토큰",
            example = "ea6b50b8-e517-4b5d-bd10-3c175db8463e")
    // [보호자 가입 흐름 8] 만 14세 미만일 때만 필수이며, 보호자 인증 확인 API가 발급한다.
    private String legalGuardianConsentToken;

}
