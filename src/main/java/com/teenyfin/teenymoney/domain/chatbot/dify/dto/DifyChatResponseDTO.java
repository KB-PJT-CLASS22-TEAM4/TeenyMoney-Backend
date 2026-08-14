package com.teenyfin.teenymoney.domain.chatbot.dify.dto;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Dify가 /chat-messages 응답으로 돌려주는 JSON을 받는 클래스.
// 실제 응답엔 task_id, message_id, metadata(usage 등) 같은 필드도 훨씬 많이 오지만,
// 우리가 실제로 쓰는 건 answer(답변 텍스트)와 conversation_id(대화 id) 둘뿐이라 나머지는 무시한다.
// TossBillingKeyResponseDTO가 토스 응답 중 billingKey/card만 뽑아 쓰는 것과 같은 이유.
@Getter
@Setter // Jackson이 JSON -> 객체로 역직렬화할 때 setter로 값을 채워 넣는다 (요청 DTO와 달리 응답 DTO는 Jackson이 "만드는" 쪽이라 Setter가 필요함)
@NoArgsConstructor // Jackson은 기본적으로 "빈 객체를 하나 만든 다음 setter로 값을 채우는" 방식으로 역직렬화하므로, 파라미터 없는 생성자
@JsonIgnoreProperties(ignoreUnknown = true) // 여기 선언 안 한 나머지 JSON 필드(message_id, metadata 등)가 와도 에러 안 내고 무시
public class DifyChatResponseDTO {

    //이번 턴 답변 텍스트 null이면(= 필드 자체가 안왔으면) DifyClient가 "응답이 이상하다"고 판단 기준
    private String answer;

    // JSON은 snake_case(conversation_id)로 오는데 자바 필드는 camelCase(conversationId)로 두고,
    // @JsonProperty로 둘을 연결해준다. 요청 DTO에서 쓴 것과 정확히 반대 방향의 매핑.
    @JsonProperty("conversation_id")
    private String conversationId;


}
