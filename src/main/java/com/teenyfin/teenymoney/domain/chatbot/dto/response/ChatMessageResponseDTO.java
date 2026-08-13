package com.teenyfin.teenymoney.domain.chatbot.dto.response;


import lombok.Getter;

// 우리 POST /chatbot/messages가 프론트에 돌려주는 응답 body 모양.
// AllowanceSendResponseDTO처럼 불변 객체(필드 전부 final, setter 없음) - 생성 후 값이 바뀔 일이 없어서.
// ChatService가 DifyChatResponseDTO(Dify가 준 응답)를 이 모양으로 변환해서 Controller에 돌려준다.
@Getter
public class ChatMessageResponseDTO {

    private final String answer;
    private final String conversationId;

    public ChatMessageResponseDTO(String answer, String conversationId) {
        this.answer = answer;
        this.conversationId = conversationId;
    }
}
