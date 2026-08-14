package com.teenyfin.teenymoney.domain.chatbot.controller;


import com.teenyfin.teenymoney.domain.chatbot.dto.request.ChatMessageRequestDTO;
import com.teenyfin.teenymoney.domain.chatbot.dto.response.ChatMessageResponseDTO;
import com.teenyfin.teenymoney.domain.chatbot.service.ChatService;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponses;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

// 챗봇 API 진입점. HTTP 요청/응답 변환만 담당하고, 실제 로직은 전부 ChatService에 위임한다
// (AllowanceController와 같은 얇은 컨트롤러 패턴).
@RestController
@RequestMapping("/chatbot")
@Api(tags = "Chatbot", description = "금융 지식 챗봇 API")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
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
            @io.swagger.annotations.ApiResponse(code = 502, message = "챗봇 응답을 받아오지 못함") })
    public ApiResponse<ChatMessageResponseDTO> sendMessage(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody ChatMessageRequestDTO request) {
        ChatMessageResponseDTO response = chatService.sendMessage(principal, request);
        return ApiResponse.ok(response);
    }
}
