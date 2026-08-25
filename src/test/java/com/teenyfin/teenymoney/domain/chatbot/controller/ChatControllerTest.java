package com.teenyfin.teenymoney.domain.chatbot.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teenyfin.teenymoney.domain.chatbot.dto.request.ChatMessageRequestDTO;
import com.teenyfin.teenymoney.domain.chatbot.dto.response.ChatMessageResponseDTO;
import com.teenyfin.teenymoney.domain.chatbot.service.ChatService;
import com.teenyfin.teenymoney.global.exception.GlobalExceptionAdvice;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

/**
 * HTTP 경계 검증 (MoneyReportControllerTest와 같은 패턴).
 *
 * ChatController는 이전까지 테스트 파일 자체가 없었다. 이번에 DeferredResult + difyTaskExecutor
 * 비동기 로직이 들어가면서 무검증 상태로 남겨두면 위험 부담이 커서 새로 작성한다.
 *
 * 두 번째 테스트(풀이 꽉 찼을 때 503)는 setUp()의 기본 크기 풀로는 재현이 안 된다 - 기본
 * ThreadPoolTaskExecutor는 core=1/max=Integer.MAX_VALUE/queue=Integer.MAX_VALUE라 사실상
 * 거절이 안 일어난다. 그래서 그 테스트만 core=1/max=1/queue=0짜리 전용 컨트롤러 인스턴스를
 * 따로 만들어 쓴다.
 */
class ChatControllerTest {

    private static final MemberPrincipal CHILD = new MemberPrincipal(2L, "CHILD");

    private ChatService chatService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        chatService = mock(ChatService.class);
        objectMapper = Jackson2ObjectMapperBuilder.json().build();

        ThreadPoolTaskExecutor difyTaskExecutor = new ThreadPoolTaskExecutor();
        difyTaskExecutor.initialize();

        mockMvc = buildMockMvc(chatService, difyTaskExecutor);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockMvc buildMockMvc(ChatService service, ThreadPoolTaskExecutor executor) {
        return MockMvcBuilders
                .standaloneSetup(new ChatController(service, executor))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private void authenticate(MemberPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private ChatMessageRequestDTO chatRequest(String query) {
        ChatMessageRequestDTO request = new ChatMessageRequestDTO();
        request.setQuery(query);
        return request;
    }

    @Test
    @DisplayName("질문을 보내면 ChatService 응답을 그대로 감싸서 내려준다")
    void sendMessageReturnsAnswerFromService() throws Exception {
        authenticate(CHILD);
        when(chatService.sendMessage(eq(CHILD), any(ChatMessageRequestDTO.class)))
                .thenReturn(new ChatMessageResponseDTO("이자는 돈을 빌려주고 받는 대가예요.", "conv-1"));

        // 1단계: 요청을 보내면 컨트롤러가 DeferredResult를 리턴하고 비동기로 전환된다.
        MvcResult mvcResult = mockMvc.perform(post("/chatbot/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chatRequest("이자가 뭐야?"))))
                .andExpect(request().asyncStarted())
                .andReturn();

        // 2단계: difyTaskExecutor 스레드가 setResult()로 채운 결과를 실제 HTTP 응답으로 디스패치.
        String body = mockMvc.perform(asyncDispatch(mvcResult))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertTrue(body.contains("\"success\":true"), body);
        assertTrue(body.contains("이자는 돈을 빌려주고 받는 대가예요."), body);
        verify(chatService).sendMessage(eq(CHILD), any(ChatMessageRequestDTO.class));
    }

    @Test
    @DisplayName("전용 풀이 꽉 차면(AbortPolicy) 503 CHATBOT_SERVER_BUSY로 즉시 거절한다")
    void sendMessageReturns503WhenPoolIsSaturated() throws Exception {
        authenticate(CHILD);

        // 이 테스트만 core=1/max=1/queue=0짜리 극단적으로 작은 풀을 쓴다 - queue=0이면 스프링이
        // 내부적으로 SynchronousQueue를 써서, 유일한 스레드가 이미 일하는 중이면 대기 없이
        // 바로 거절된다 (TransactionalTest 관련 다른 파일들과 달리 이 테스트는 "빨리 거절되는지"가
        // 검증 대상이라 대기가 조금이라도 있으면 안 된다).
        ThreadPoolTaskExecutor tinyExecutor = new ThreadPoolTaskExecutor();
        tinyExecutor.setCorePoolSize(1);
        tinyExecutor.setMaxPoolSize(1);
        tinyExecutor.setQueueCapacity(0);
        tinyExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        tinyExecutor.initialize();
        MockMvc tinyMockMvc = buildMockMvc(chatService, tinyExecutor);

        // 유일한 스레드를 "영원히 안 끝나는 작업"으로 묶어둔다 - 이 latch를 우리가 직접 풀어주기
        // 전까지는 ChatService.sendMessage()가 리턴을 안 하므로, 그 스레드가 계속 점유된 상태로 남는다.
        CountDownLatch blockFirstCall = new CountDownLatch(1);
        when(chatService.sendMessage(eq(CHILD), any(ChatMessageRequestDTO.class)))
                .thenAnswer(invocation -> {
                    blockFirstCall.await();
                    return new ChatMessageResponseDTO("첫 번째 응답", "conv-1");
                });

        try {
            // 첫 번째 요청: 유일한 스레드를 점유하고 블로킹된 채로 남는다 (비동기로 전환은 정상적으로 됨).
            tinyMockMvc.perform(post("/chatbot/messages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(chatRequest("이자가 뭐야?"))))
                    .andExpect(request().asyncStarted());

            // 두 번째 요청: 유일한 스레드가 이미 첫 번째 작업으로 점유돼 있고 대기 큐도 0이라,
            // TaskRejectedException이 즉시(동기적으로) 발생 -> 컨트롤러가 CHATBOT_SERVER_BUSY로 변환.
            // 이 응답은 비동기로 안 넘어가고 Tomcat(테스트) 스레드 위에서 바로 완결된다.
            var response = tinyMockMvc.perform(post("/chatbot/messages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(chatRequest("용돈이 뭐야?"))))
                    .andReturn().getResponse();
            String body = response.getContentAsString(StandardCharsets.UTF_8);

            assertEquals(503, response.getStatus(), body);
            assertTrue(body.contains("\"code\":\"CHATBOT_SERVER_BUSY\""), body);
        } finally {
            // 첫 번째 작업을 풀어줘서 스레드가 테스트 종료 후에도 계속 블로킹된 채로 남지 않게 한다.
            blockFirstCall.countDown();
            tinyExecutor.shutdown();
        }
    }
}
