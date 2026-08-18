package com.teenyfin.teenymoney.domain.member.dto.response;

import com.teenyfin.teenymoney.domain.member.vo.AgreementVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 약관 응답. 목록과 상세가 필드 하나 차이라 DTO를 나누지 않는다.
 *
 * 목록 조회에서는 content가 null로 내려간다. FE는 목록에서 제목·버전만 그리고,
 * 전문이 필요하면 code로 상세를 다시 부른다.
 */
@Getter
@ApiModel(description = "약관 정보")
public class AgreementResponseDTO {

    @ApiModelProperty(value = "약관 ID", example = "1")
    private final Long id;
    @ApiModelProperty(value = "약관 코드", example = "SERVICE_TERMS")
    private final String code;
    @ApiModelProperty(value = "약관 버전", example = "1.0")
    private final String version;
    @ApiModelProperty(value = "약관 제목", example = "서비스 이용약관")
    private final String title;
    @ApiModelProperty(
            value = "약관 전문. 목록 조회 응답에서는 항상 null이며 상세 조회에서만 채워진다.",
            example = "제1조(목적) 본 약관은 ...")
    private final String content;
    @ApiModelProperty(value = "필수 동의 여부", example = "true")
    private final Boolean isRequired;
    @ApiModelProperty(value = "적용 시작 일시", example = "2026-08-04T00:00:00")
    private final LocalDateTime effectiveAt;

    private AgreementResponseDTO(AgreementVO agreement) {
        this.id = agreement.getId();
        this.code = agreement.getCode();
        this.version = agreement.getVersion();
        this.title = agreement.getTitle();
        this.content = agreement.getContent();
        this.isRequired = agreement.getIsRequired();
        this.effectiveAt = agreement.getEffectiveAt();
    }

    public static AgreementResponseDTO of(AgreementVO agreement) {
        return new AgreementResponseDTO(agreement);
    }
}
