package com.teenyfin.teenymoney.domain.chatbot.exception;

import com.teenyfin.teenymoney.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

// 챗봇(Dify 연동) 도메인 전용 에러 코드.
@Getter // status, message 필드의 getter를 자동 생성 (ErrorCode 인터페이스가 요구하는 getStatus()/getMessage())
@RequiredArgsConstructor // final 필드(status, message) 2개를 받는 생성자를 자동 생성 - 아래 각 enum 상수가 이 생성자를 호출하는 것

public enum ChatbotErrorCode implements ErrorCode {    // Dify가 4xx/5xx로 응답했거나, 네트워크 자체가 실패한 경우 (타임아웃 등)
    CHATBOT_REQUEST_FAILED(
            HttpStatus.BAD_GATEWAY, "챗봇 응답을 받아오지 못했습니다. 잠시 후 다시 시도해주세요."),
    // Dify가 200을 줬는데 응답 안에 answer 필드가 없는 등, 형식 자체가 이상한 경우
    CHATBOT_RESPONSE_INVALID(
            HttpStatus.BAD_GATEWAY, "챗봇 응답이 올바르지 않습니다."),
    // conversationId는 있는데, Redis에 저장된 소유자가 없거나(=모름) 나와 다른 경우.
    // 403을 씀 - 인증(로그인) 문제가 아니라 "너는 이 자원에 접근 권한이 없다"는 뜻이라 401이 아니라 403이 맞음.
    CHATBOT_CONVERSATION_FORBIDDEN(
            HttpStatus.FORBIDDEN, "본인 소유의 대화가 아닙니다."),
    CHATBOT_API_KEY_MISSING(HttpStatus.SERVICE_UNAVAILABLE, "챗봇 API 설정이 필요합니다."),
    // 우리 서버의 Dify 전용 스레드풀이 꽉 차서 거절한 경우 - Dify 자체 실패(CHATBOT_REQUEST_FAILED,
    // 502)와는 원인이 다르다. 이걸 구분해둬야 나중에 장애 로그에서 "Dify가 죽은 건지 우리 풀이
    // 혼잡한 건지"를 바로 알 수 있다.
    CHATBOT_SERVER_BUSY(HttpStatus.SERVICE_UNAVAILABLE, "지금 요청이 많아 잠시 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String message;

    // ErrorCode 인터페이스가 요구하는 메서드. code 값은 항상 enum 상수 이름 그대로 씀
    // (예: "CHATBOT_REQUEST_FAILED") - 이름과 응답 code가 어긋날 일이 없게.
    @Override
    public String getCode() {
        return name();
    }
}
