package com.teenyfin.teenymoney.domain.chatbot.dify.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

// Dify /chat-messages에 보낼 요청 body.
// 이 Dify 앱은 표준 query 외에 커스텀 시작 폼 변수 inputs.question도 필수로 요구해서
// (실 서버 curl 테스트로 확인: 없으면 400 "question is required in input form"),
// query 값을 query와 inputs.question 양쪽에 동일하게 채운다.
@Getter
public class DifyChatRequestDTO {
    // Dify 앱의 "대화 시작 폼" 변수들. 이 앱은 question이라는 변수 하나를 요구하므로
    // Map.of("question", query) 형태로 채운다 - JSON으로는 "inputs": {"question": "..."}가 됨
    private final Map<String, String> inputs;

    // Dify가 실제로 답변을 생성할 때 참고하는 "이번 턴 사용자 질문" 본문
    private final String query;


    // @JsonProperty("response_mode"): 자바 필드명은 카멜케이스(responseMode)로 쓰되,
    // 실제 JSON 필드명은 Dify가 요구하는 스네이크케이스(response_mode)로 나가게 강제하는 어노테이션.
    // 이게 없으면 Jackson은 기본적으로 자바 필드명 그대로("responseMode")를 JSON 키로 써버려서
    // Dify가 이 필드를 못 알아본다.
    @JsonProperty("response_mode")
    private final String responseMode;


    // 이전 턴 대화를 이어가려면 Dify가 이전에 돌려준 conversation_id를 그대로 실어 보내야 함.
    // 없으면(신규 대화) 빈 문자열("")을 보내는 게 Dify 쪽 규칙.
    @JsonProperty("conversation_id")
    private final String conversationId;

    // Dify 쪽에서 "이 요청을 보낸 사람이 누구인지" 구분하는 식별자.
    // 우리 서버의 로그인/인증과는 별개 개념 - ChatService가 "member-{memberId}" 형태로 채워서 넘겨줌.
    private final String user;

    // 파일(이미지 등) 첨부 목록. 이번 범위에서는 첨부를 지원하지 않으므로 항상 빈 리스트로 고정.
    private final List<Object> files;

    public DifyChatRequestDTO(String query, String conversationId, String user) {
        this.inputs = Map.of("question", query); // query와 동일한 값을 inputs.question에도 채움 (위 클래스 설명 참고)
        this.query = query;
        this.responseMode = "blocking";
        this.conversationId = conversationId == null ? "" : conversationId; // null이면 "새 대화 시작"을 뜻하는 빈 문자열로 변환
        this.user = user;
        this.files = Collections.emptyList(); // 항상 빈 배열 - 첨부 미지원이므로 파라미터로도 안 받음

    }
}
