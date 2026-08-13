package com.teenyfin.teenymoney.domain.chatbot.service;


import com.teenyfin.teenymoney.domain.chatbot.dify.DifyClient;
import com.teenyfin.teenymoney.domain.chatbot.dify.dto.DifyChatResponseDTO;
import com.teenyfin.teenymoney.domain.chatbot.dto.request.ChatMessageRequestDTO;
import com.teenyfin.teenymoney.domain.chatbot.dto.response.ChatMessageResponseDTO;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.springframework.stereotype.Service;

// Controller와 DifyClient 사이를 잇는 얇은 서비스.
// 여기서 하는 일은 딱 두 가지: (1) 로그인한 사람(memberId)을 Dify user 식별자로 바꿔주는 것,
// (2) Dify 전용 응답(DifyChatResponseDTO)을 우리 API 응답(ChatMessageResponseDTO)으로 바꿔주는 것.
@Service
public class ChatService {

    private final DifyClient difyClient;

    public ChatService(DifyClient difyClient) {
        this.difyClient = difyClient;
    }

    public ChatMessageResponseDTO sendMessage(MemberPrincipal principal, ChatMessageRequestDTO request) {
        // memberId를 "member-{memberId}"로 매핑 - 우리 서버 접근 제어(로그인 여부)와는 별개로,
        // Dify 쪽에서 대화 소유자를 구분하기 위한 식별자다. 가족 구성원끼리 conversation_id가
        // 섞이지 않게 하는 역할 (예: 아빠가 자녀의 conversation_id를 알아도 이어갈 수 없음).
        String user = "member-" + principal.memberId();

        DifyChatResponseDTO response = difyClient.sendMessage(request.getQuery(), request.getConversationId(), user);

        return new ChatMessageResponseDTO(response.getAnswer(), response.getConversationId());
    }
}
