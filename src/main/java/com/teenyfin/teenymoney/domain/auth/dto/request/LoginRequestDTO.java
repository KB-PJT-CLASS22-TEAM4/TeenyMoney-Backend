package com.teenyfin.teenymoney.domain.auth.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Getter
@Setter
@NoArgsConstructor
@ApiModel(description = "로그인 요청")
public class LoginRequestDTO {

    @ApiModelProperty(value = "가입한 이메일", required = true, example = "user@example.com")
    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
    private String email;

    @ApiModelProperty(value = "비밀번호", required = true, example = "Abcd!2345")
    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(max = 64, message = "비밀번호는 64자 이하여야 합니다.")
    private String password;

    public void setEmail(String email) {
        this.email = email == null ? null : email.trim();
    }
}
