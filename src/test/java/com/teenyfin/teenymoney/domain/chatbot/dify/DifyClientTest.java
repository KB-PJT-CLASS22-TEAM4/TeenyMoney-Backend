package com.teenyfin.teenymoney.domain.chatbot.dify;

import com.teenyfin.teenymoney.domain.chatbot.dify.dto.DifyChatResponseDTO;
import com.teenyfin.teenymoney.domain.chatbot.exception.ChatbotErrorCode;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DifyClientTest {

    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
    }

    @Test
    @DisplayName("api-key가 null이거나 공백이면 isConfigured()는 false다")
    void isConfiguredIsFalseWhenApiKeyMissing() {
        assertFalse(client(null).isConfigured());
        assertFalse(client("   ").isConfigured());
    }

    @Test
    @DisplayName("api-key가 실제로 있으면 isConfigured()는 true다")
    void isConfiguredIsTrueWhenApiKeyPresent() {
        assertTrue(client("real-api-key").isConfigured());
    }

    @Test
    @DisplayName("api-key가 없으면 RestTemplate을 건드리지도 않고 바로 막는다")
    void sendMessageFailsFastWhenApiKeyMissing() {
        DifyClient client = client("");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> client.sendMessage("이자가 뭐야?", null, "member-1"));

        assertEquals(ChatbotErrorCode.CHATBOT_API_KEY_MISSING, exception.getErrorCode());
        // 키가 없다는 걸 확인한 시점에서 바로 던져야지, 네트워크 호출까지 나가면 안 된다
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("Dify가 정상 응답을 주면 그대로 돌려준다")
    void sendMessageReturnsAnswerOnSuccess() {
        DifyClient client = client("real-api-key");
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(DifyChatResponseDTO.class)))
                .thenReturn(difyResponse("이자는 돈을 빌려주고 받는 대가예요.", "conv-1"));

        DifyChatResponseDTO response = client.sendMessage("이자가 뭐야?", null, "member-1");

        assertEquals("이자는 돈을 빌려주고 받는 대가예요.", response.getAnswer());
        assertEquals("conv-1", response.getConversationId());
    }

    @Test
    @DisplayName("200을 받았는데 body 자체가 없으면 응답이 이상하다고 판단한다")
    void sendMessageFailsWhenResponseBodyMissing() {
        DifyClient client = client("real-api-key");
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(DifyChatResponseDTO.class)))
                .thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> client.sendMessage("이자가 뭐야?", null, "member-1"));

        assertEquals(ChatbotErrorCode.CHATBOT_RESPONSE_INVALID, exception.getErrorCode());
    }

    @Test
    @DisplayName("200을 받았는데 answer 필드가 없으면 응답이 이상하다고 판단한다")
    void sendMessageFailsWhenAnswerFieldMissing() {
        DifyClient client = client("real-api-key");
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(DifyChatResponseDTO.class)))
                .thenReturn(difyResponse(null, "conv-1"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> client.sendMessage("이자가 뭐야?", null, "member-1"));

        assertEquals(ChatbotErrorCode.CHATBOT_RESPONSE_INVALID, exception.getErrorCode());
    }

    @Test
    @DisplayName("Dify가 4xx/5xx로 응답하면 CHATBOT_REQUEST_FAILED로 변환한다")
    void sendMessageFailsWhenDifyReturnsErrorStatus() {
        DifyClient client = client("real-api-key");
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(DifyChatResponseDTO.class)))
                .thenThrow(new HttpClientErrorException(
                        HttpStatus.BAD_REQUEST,
                        "Bad Request",
                        "{\"code\":\"invalid_param\"}".getBytes(StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> client.sendMessage("이자가 뭐야?", null, "member-1"));

        assertEquals(ChatbotErrorCode.CHATBOT_REQUEST_FAILED, exception.getErrorCode());
    }

    @Test
    @DisplayName("타임아웃/연결 실패 등 네트워크 자체가 안 되면 CHATBOT_REQUEST_FAILED로 변환한다")
    void sendMessageFailsWhenNetworkCallFails() {
        DifyClient client = client("real-api-key");
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(DifyChatResponseDTO.class)))
                .thenThrow(new RestClientException("Connection timed out"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> client.sendMessage("이자가 뭐야?", null, "member-1"));

        assertEquals(ChatbotErrorCode.CHATBOT_REQUEST_FAILED, exception.getErrorCode());
    }

    private DifyClient client(String apiKey) {
        return new DifyClient(restTemplate, apiKey, "https://api.dify.ai/v1");
    }

    // DifyChatResponseDTO는 NoArgsConstructor + Setter라서(Jackson 역직렬화용), 테스트에서도 같은 방식으로 만든다
    private DifyChatResponseDTO difyResponse(String answer, String conversationId) {
        DifyChatResponseDTO response = new DifyChatResponseDTO();
        response.setAnswer(answer);
        response.setConversationId(conversationId);
        return response;
    }
}
