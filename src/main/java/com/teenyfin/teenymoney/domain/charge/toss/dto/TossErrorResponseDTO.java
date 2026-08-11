package com.teenyfin.teenymoney.domain.charge.toss.dto;

// 토스가 실패(4xx/5xx)를 응답할 때 오는 에러 body 모양

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 토스 API가 실패하면 {"code": "INVALID_CARD_EXPIRATION", "message": "카드 유효기간이 ..."}
// 같은 형태로 응답함. 이걸 파싱해서 우리 서버 로그에 "왜 실패했는지" 정확히 남기려고 만든 DTO.
// (실제 응답을 사용자에게 그대로 보여주진 않음 - 아래 TossPaymentsClient 설명 참고)
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TossErrorResponseDTO {
    private String code;
    private String message;
}
