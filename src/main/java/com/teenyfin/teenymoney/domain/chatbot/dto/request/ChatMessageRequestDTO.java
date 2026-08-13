package com.teenyfin.teenymoney.domain.chatbot.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotBlank;


// 프론트가 우리 POST /chatbot/messages 호출할 때 보내는 요청 body 모양.
// DifyChatRequestDTO(Dify한테 보내는 모양)와는 완전히 다른 별개 클래스 - 우리 API 계약은
// 훨씬 단순하게(query, conversationId 두 개만) 유지하고, 나머지(inputs, response_mode, files,
// user)는 ChatService/DifyClient 내부에서 채워 넣는다.
@Getter
@Setter
@NoArgsConstructor // Jackson이 요청 JSON을 이 객체로 역직렬화할 때 빈 객체 먼저 만든 다음 setter로 채움
@ApiModel(description = "챗봇 질문 요청")
public class ChatMessageRequestDTO {

    @ApiModelProperty(value = "이번 턴 질문", required = true, example = "이자가 뭐야?")
    @NotBlank(message = "질문 내용은 필수 입니다.") // 빈 문자열 / 공백만 있는 값 / null 전부 막음
    private String query;

    @ApiModelProperty(value = "이전 턴에서 받은 대화 id. 없으면 새 대화로 시작", required = false)
    private String conversationId; // 필수 아님 - 첫 질문이면 안 보내도 됨 (null 허용)
}
