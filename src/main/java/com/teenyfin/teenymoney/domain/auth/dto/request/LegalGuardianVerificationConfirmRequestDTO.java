package com.teenyfin.teenymoney.domain.auth.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Getter
@Setter
@NoArgsConstructor
@ApiModel(description = "법정대리인 SMS 인증 확인 요청")
// [보호자 가입 흐름 3] 보호자 SMS 인증 확인과 동의 토큰 발급에 필요한 입력값이다.
public class LegalGuardianVerificationConfirmRequestDTO {

    @ApiModelProperty(value = "법정대리인 한글 실명", required = true, example = "김보호")
    @NotBlank(message = "보호자 이름은 필수입니다.")
    @Size(min = 2, max = 7, message = "보호자 이름은 2~7자여야 합니다.")
    @Pattern(regexp = "^[가-힣]+$", message = "보호자 이름은 한글로 입력해야 합니다.")
    private String legalGuardianName; // 인증 및 이력에 저장할 법정대리인 실명

    @ApiModelProperty(
            value = "가입자와의 관계",
            required = true,
            allowableValues = "FATHER,MOTHER,OTHER_LEGAL_GUARDIAN",
            example = "MOTHER")
    @NotBlank(message = "보호자 관계는 필수입니다.")
    @Pattern(
            regexp = "^(FATHER|MOTHER|OTHER_LEGAL_GUARDIAN)$",
            message = "보호자 관계가 올바르지 않습니다.")
    private String relationship; // 자녀와의 관계: 부/모/기타 법정대리인

    @ApiModelProperty(value = "SMS 인증을 수행할 법정대리인 휴대폰 번호", required = true, example = "010-1234-5678")
    @NotBlank(message = "보호자 휴대폰 번호는 필수입니다.")
    @Pattern(
            regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$",
            message = "보호자 휴대폰 번호 형식이 올바르지 않습니다.")
    private String phoneNumber; // SMS 인증을 수행한 보호자 휴대폰 번호

    @ApiModelProperty(value = "문자로 받은 6자리 인증번호", required = true, example = "123456")
    @NotBlank(message = "보호자 인증번호는 필수입니다.")
    @Pattern(regexp = "^\\d{6}$", message = "보호자 인증번호는 숫자 6자리여야 합니다.")
    private String verificationCode; // Redis에 저장된 값과 비교할 6자리 코드

    @ApiModelProperty(value = "법정대리인 동의 여부", required = true, example = "true")
    @NotNull(message = "법정대리인 동의 여부는 필수입니다.")
    @AssertTrue(message = "법정대리인 동의가 필요합니다.")
    private Boolean legalGuardianTermsAgreed; // false는 Bean Validation 단계에서 거부한다.

    @ApiModelProperty(value = "동의한 서비스 이용약관 버전", required = true, example = "1.0")
    @NotBlank(message = "서비스 약관 버전은 필수입니다.")
    private String serviceTermsVersion; // 보호자가 동의한 서비스 약관 버전

    @ApiModelProperty(value = "동의한 개인정보 약관 버전", required = true, example = "1.0")
    @NotBlank(message = "개인정보 약관 버전은 필수입니다.")
    private String privacyTermsVersion; // 보호자가 동의한 개인정보 약관 버전
}
