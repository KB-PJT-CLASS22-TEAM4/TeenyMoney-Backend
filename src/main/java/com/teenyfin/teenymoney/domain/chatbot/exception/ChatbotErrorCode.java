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
            HttpStatus.BAD_GATEWAY, "챗봇 응답이 올바르지 않습니다.");

    private final HttpStatus status;
    private final String message;

    // ErrorCode 인터페이스가 요구하는 메서드. code 값은 항상 enum 상수 이름 그대로 씀
    // (예: "CHATBOT_REQUEST_FAILED") - 이름과 응답 code가 어긋날 일이 없게.
    @Override
    public String getCode() {
        return name();
    }
}
