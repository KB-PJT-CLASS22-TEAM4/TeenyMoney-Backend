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
    private String profileImageUrl;
    private String status;
}
