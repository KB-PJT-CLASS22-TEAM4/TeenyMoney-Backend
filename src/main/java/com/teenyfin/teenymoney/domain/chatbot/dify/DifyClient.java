package com.teenyfin.teenymoney.domain.chatbot.dify;


import com.teenyfin.teenymoney.domain.chatbot.dify.dto.DifyChatRequestDTO;
import com.teenyfin.teenymoney.domain.chatbot.dify.dto.DifyChatResponseDTO;
import com.teenyfin.teenymoney.domain.chatbot.exception.ChatbotErrorCode;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;


// Dify Chat Messages API(/v1/chat-messages)와 직접 통신하는 담당 클래스.
// TossPaymentsClient/FinlifeClient랑 똑같은 역할 - "외부 API 하나를 감싸는 전용 컴포넌트".
// ChatService는 Dify의 URL이나 인증 헤더 형식을 전혀 몰라도 되고, 이 클래스의 sendMessage()만 호출하면 됨.
@Slf4j          // Lombok이 log.warn(...) 등을 쓸 수 있게 로거 필드를 자동 생성
@Component      // 스프링 빈으로 등록 - @Autowired 없이도 생성자 주입 가능
public class DifyClient {

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String baseUrl;

    // RestTemplate: RestTemplateConfig가 등록해둔 공용 빈을 그대로 주입받음 (토스/finlife와 동일).
    // apiKey/baseUrl: application.properties의 dify.api-key / dify.base-url 값을 @Value로 주입받음.
    @Autowired
    public DifyClient(RestTemplate restTemplate, @Value("${dify.api-key}") String apiKey, @Value("${dify.base-url:https://api.dify.ai/v1}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    // query(이번 턴 질문), conversationId(이전 대화 이어가기용, 없으면 null), user(Dify쪽 사용자 식별자)를
    // 받아서 Dify에 질문을 보내고 답변을 받아온다.
    public DifyChatResponseDTO sendMessage(String query, String conversationId, String user) {

        if(!isConfigured()) {
            throw new BusinessException(ChatbotErrorCode.CHATBOT_API_KEY_MISSING);
        }

        String url = baseUrl + "/chat-messages";
        DifyChatRequestDTO request = new DifyChatRequestDTO(query, conversationId, user);

        // HttpEntity: "요청 body + 요청 헤더"를 한 묶음으로 들고 있는 객체 (TossPaymentsClient와 동일한 패턴)
        HttpEntity<DifyChatRequestDTO> entity = new HttpEntity<>(request, buildAuthHeaders());

        try {
            // postForObject(url, 보낼 것, 받을 응답 타입) - HTTP POST 요청을 보내고,
            // 응답 JSON을 자동으로 DifyChatResponseDTO 객체로 변환해서 리턴해줌.
            DifyChatResponseDTO response = restTemplate.postForObject(url, entity, DifyChatResponseDTO.class);

            if (response == null || response.getAnswer() == null) {
                //200을 받았는데 정작 answer가 없는 비정상 응답 -
                throw new BusinessException(ChatbotErrorCode.CHATBOT_RESPONSE_INVALID);
            }
            return response;
        }catch (HttpStatusCodeException exception) {
            // Dify가 4xx/5xx로 응답한 경우. getResponseBodyAsString()엔 Dify 자신의 에러 설명
            // (예: {"code":"invalid_param","message":"question is required in input form"})이 담겨있어서
            // 로그에 남겨도 안전함 - 사용자가 입력한 질문(query)이 아니기 때문.
            log.warn("Dify API 오류 - status: {}, body: {}", exception.getStatusCode(), exception.getResponseBodyAsString());
            throw new BusinessException(ChatbotErrorCode.CHATBOT_REQUEST_FAILED);
        } catch (RestClientException exception) {
            // 타임아웃, 연결 실패 등 - 사용자 질문(query)은 여기서도 절대 로그에 남기지 않는다.
            log.warn("Dify API 호출 실패", exception);
            throw new BusinessException(ChatbotErrorCode.CHATBOT_REQUEST_FAILED);
        }
    }
    // Dify API를 호출할 때마다 공통으로 필요한 인증 헤더를 만든다.
    private HttpHeaders buildAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        // Dify는 "Bearer 토큰" 인증 방식을 씀 - Authorization: Bearer {api_key}
        // setBearerAuth()가 "Bearer " 접두사까지 알아서 붙여줌 (토스의 Basic 인증과는 다른 방식)
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON); // 우리가 보내는 body가 JSON이라고 알려줌
        return headers;
    }

}
