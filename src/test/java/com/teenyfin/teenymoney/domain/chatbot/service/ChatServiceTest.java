package com.teenyfin.teenymoney.domain.chatbot.service;

import com.teenyfin.teenymoney.domain.chatbot.dify.DifyClient;
import com.teenyfin.teenymoney.domain.chatbot.dify.dto.DifyChatResponseDTO;
import com.teenyfin.teenymoney.domain.chatbot.dto.request.ChatMessageRequestDTO;
import com.teenyfin.teenymoney.domain.chatbot.dto.response.ChatMessageResponseDTO;
import com.teenyfin.teenymoney.domain.chatbot.exception.ChatbotErrorCode;
import com.teenyfin.teenymoney.domain.chatbot.store.ChatConversationOwnerStore;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceTest {

    private static final MemberPrincipal ME = new MemberPrincipal(1L, "CHILD");
    private static final MemberPrincipal OTHER = new MemberPrincipal(2L, "CHILD");

    private DifyClient difyClient;
    private ChatConversationOwnerStore conversationOwnerStore;
    private ChatService service;

    @BeforeEach
    void setUp() {
        difyClient = mock(DifyClient.class);
        conversationOwnerStore = mock(ChatConversationOwnerStore.class);
        service = new ChatService(difyClient, conversationOwnerStore);
    }

    @Test
    @DisplayName("conversationId 없이 보내면 소유권 검사 없이 바로 진행하고, 새로 받은 conversationId를 저장한다")
    void newConversationSkipsOwnershipCheckAndSavesOwner() {
        ChatMessageRequestDTO request = request("이자가 뭐야?", null);
        when(difyClient.sendMessage("이자가 뭐야?", null, "member-1"))
                .thenReturn(difyResponse("이자는...", "new-conv-id"));

        ChatMessageResponseDTO response = service.sendMessage(ME, request);

        // conversationId가 없었으니 "누구 것인지 확인할 대상 자체가 없다" - 조회를 시도조차 하면 안 된다
        verify(conversationOwnerStore, never()).findOwner(anyString());
        // Dify가 새로 내려준 conversationId를 내 소유로 기록해야 다음 요청에서 이어갈 수 있다
        verify(conversationOwnerStore).saveOwner("new-conv-id", 1L);
        assertEquals("이자는...", response.getAnswer());
        assertEquals("new-conv-id", response.getConversationId());
    }

    @Test
    @DisplayName("내가 소유한 conversationId면 정상적으로 이어간다")
    void continuingOwnConversationSucceeds() {
        ChatMessageRequestDTO request = request("더 자세히 알려줘", "conv-1");
        when(conversationOwnerStore.findOwner("conv-1")).thenReturn(1L);
        when(difyClient.sendMessage("더 자세히 알려줘", "conv-1", "member-1"))
                .thenReturn(difyResponse("더 자세히 설명하면...", "conv-1"));

        ChatMessageResponseDTO response = service.sendMessage(ME, request);

        assertEquals("더 자세히 설명하면...", response.getAnswer());
        // 이어가는 대화도 다시 저장한다 - saveOwner()가 TTL을 갱신해서 계속 쓰는 대화가 안 끊기게 함
        verify(conversationOwnerStore).saveOwner("conv-1", 1L);
    }

    @Test
    @DisplayName("다른 사람 소유의 conversationId면 Dify에 보내지도 않고 403으로 막는다")
    void otherMembersConversationIsForbidden() {
        ChatMessageRequestDTO request = request("아빠가 뭐 물어봤었지?", "conv-1");
        when(conversationOwnerStore.findOwner("conv-1")).thenReturn(OTHER.memberId());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.sendMessage(ME, request));

        assertEquals(ChatbotErrorCode.CHATBOT_CONVERSATION_FORBIDDEN,
                exception.getErrorCode());
        // 소유자가 아니라고 판단되면 Dify 호출 자체가 나가면 안 된다 (남의 대화 내용을 조회하게 되므로)
        verify(difyClient, never()).sendMessage(anyString(), anyString(), anyString());
        verify(conversationOwnerStore, never()).saveOwner(anyString(), any());
    }

    @Test
    @DisplayName("소유자를 모르는(=저장된 적 없거나 TTL 만료된) conversationId도 403으로 막는다")
    void unknownConversationIsForbidden() {
        ChatMessageRequestDTO request = request("이어서 설명해줘", "conv-1");
        when(conversationOwnerStore.findOwner("conv-1")).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.sendMessage(ME, request));

        assertEquals(ChatbotErrorCode.CHATBOT_CONVERSATION_FORBIDDEN,
                exception.getErrorCode());
        verify(difyClient, never()).sendMessage(anyString(), anyString(), anyString());
    }

    // ChatMessageRequestDTO는 NoArgsConstructor + Setter라서(Jackson 역직렬화용), 테스트에서도 같은 방식으로 만든다
    private ChatMessageRequestDTO request(String query, String conversationId) {
        ChatMessageRequestDTO request = new ChatMessageRequestDTO();
        request.setQuery(query);
        request.setConversationId(conversationId);
        return request;
    }

    // DifyChatResponseDTO도 NoArgsConstructor + Setter라서(Jackson 역직렬화용), 테스트에서도 같은 방식으로 만든다
    private DifyChatResponseDTO difyResponse(String answer, String conversationId) {
        DifyChatResponseDTO response = new DifyChatResponseDTO();
        response.setAnswer(answer);
        response.setConversationId(conversationId);
        return response;
    }
}
