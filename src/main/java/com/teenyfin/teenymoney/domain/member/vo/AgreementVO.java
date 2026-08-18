package com.teenyfin.teenymoney.domain.member.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * T_MBR_AGRMT_M 한 행. 약관 원본이다.
 *
 * content는 목록 조회에서 SELECT하지 않으므로 null일 수 있다. 약관 전문은 길어서
 * 목록에 실으면 화면 하나가 수십 KB를 받는다.
 */
@Getter
@Setter
@NoArgsConstructor
public class AgreementVO {

    private Long id;
    // SERVICE_TERMS / PRIVACY 같은 약관 종류 식별자. version과 묶여 UNIQUE.
    private String code;
    private String version;
    private String title;
    // 목록 조회에서는 null. 상세 조회에서만 채워진다.
    private String content;
    private Boolean isRequired;
    private LocalDateTime effectiveAt;
}
