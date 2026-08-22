package com.teenyfin.teenymoney.domain.chatbot.controller;


import com.teenyfin.teenymoney.domain.chatbot.dto.request.ChatMessageRequestDTO;
import com.teenyfin.teenymoney.domain.chatbot.dto.response.ChatMessageResponseDTO;
import com.teenyfin.teenymoney.domain.chatbot.exception.ChatbotErrorCode;
import com.teenyfin.teenymoney.domain.chatbot.service.ChatService;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import javax.validation.Valid;
import java.util.concurrent.ThreadPoolExecutor;

// 챗봇 API 진입점. HTTP 요청/응답 변환만 담당하고, 실제 로직은 전부 ChatService에 위임한다
// (AllowanceController와 같은 얇은 컨트롤러 패턴).
@RestController
@RequestMapping("/chatbot")
@Api(tags = "Chatbot", description = "금융 지식 챗봇 API")
public class ChatController {

    // difyRestTemplate의 readTimeout(120초)보다 반드시 길어야 한다 - 짧으면 우리가 클라이언트한테
    // 이미 실패 응답을 보낸 뒤에도 dify-N 스레드가 최대 120초까지 계속 붙잡혀 있게 된다.
    private static final long DIFY_DEFERRED_RESULT_TIMEOUT_MILLISECONDS = 135_000L;

    private final ChatService chatService;
    private final ThreadPoolTaskExecutor difyTaskExecutor;

    public ChatController(ChatService chatService, @Qualifier("difyTaskExecutor") ThreadPoolTaskExecutor difyTaskExecutor) {
        this.chatService = chatService;
        this.difyTaskExecutor = difyTaskExecutor;
    }

    // PARENT/CHILD 둘 다 로그인만 되어있으면 호출 가능 - 별도 @PreAuthorize 없음
    // (SecurityConfig의 기본 인증 요구사항만 적용됨).
    @PostMapping("/messages")
    @ApiOperation(
            value = "챗봇에게 질문하기",
            notes = "금융 지식 챗봇에게 질문을 보내고 답변을 받습니다.\n\n"
                    + "conversationId를 함께 보내면 이전 대화에 이어서 답변합니다. "
                    + "없으면 새 대화로 시작합니다. 대화 내용은 서버에 저장되지 않습니다.\n"
                    + "** 이전 대화가 이어지고 싶다면 프론트에서 conversationId를 저장해 그 값을 전달 해야합니다 **",
            authorizations = {
                    @io.swagger.annotations.Authorization(value = "JWT")
            }
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "답변 성공"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "질문 내용이 비어있음"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "로그인 필요"),
            @io.swagger.annotations.ApiResponse(code = 403, message = "본인 소유의 대화가 아님"),
            @io.swagger.annotations.ApiResponse(code = 502, message = "챗봇 응답을 받아오지 못함"),
            @io.swagger.annotations.ApiResponse(code = 503, message = "요청이 많아 서버가 혼잡함") })
    public DeferredResult<ApiResponse<ChatMessageResponseDTO>> sendMessage(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody ChatMessageRequestDTO request) {

        DeferredResult<ApiResponse<ChatMessageResponseDTO>> deferredResult = new DeferredResult<>(DIFY_DEFERRED_RESULT_TIMEOUT_MILLISECONDS);

        // 타임아웃 시 GlobalExceptionAdvice가 동기 호출 때와 똑같이 처리하도록
        // BusinessException을 그대로 setErrorResult에 넘긴다.
        deferredResult.onTimeout(() -> deferredResult.setErrorResult(
                new BusinessException(ChatbotErrorCode.CHATBOT_REQUEST_FAILED)));

        try {
            // 실제 Dify 호출(블로킹)을 전용 풀에서 실행 - 이 블록 안 코드는 Tomcat 스레드가 아니라
            // difyTaskExecutor가 관리하는 "dify-N" 스레드에서 실행된다.
            difyTaskExecutor.execute(() -> {
                try {
                    ChatMessageResponseDTO response = chatService.sendMessage(principal, request);
                    deferredResult.setResult(ApiResponse.ok(response));
                } catch (Exception e) {
                    deferredResult.setErrorResult(e);
                }
            });
        } catch (TaskRejectedException e) {
            // 풀+큐가 전부 꽉 차서 AbortPolicy가 거부한 경우 - 아직 Tomcat 스레드 위에서
            // 실행 중인 동기 코드라 여기서 바로 잡힌다.
            throw new BusinessException(ChatbotErrorCode.CHATBOT_SERVER_BUSY);
        }

        // Tomcat 스레드는 여기서 바로 반납된다 - Dify 응답을 기다리지 않는다.
        return deferredResult;
    }

}
