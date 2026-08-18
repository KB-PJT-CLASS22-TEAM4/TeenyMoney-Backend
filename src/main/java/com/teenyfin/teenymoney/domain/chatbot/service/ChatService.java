package com.teenyfin.teenymoney.domain.chatbot.service;


import com.teenyfin.teenymoney.domain.chatbot.dify.DifyClient;
import com.teenyfin.teenymoney.domain.chatbot.dify.dto.DifyChatResponseDTO;
import com.teenyfin.teenymoney.domain.chatbot.dto.request.ChatMessageRequestDTO;
import com.teenyfin.teenymoney.domain.chatbot.dto.response.ChatMessageResponseDTO;
import com.teenyfin.teenymoney.domain.chatbot.exception.ChatbotErrorCode;
import com.teenyfin.teenymoney.domain.chatbot.store.ChatConversationOwnerStore;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

// Controller와 DifyClient 사이를 잇는 얇은 서비스.
// 여기서 하는 일은 딱 두 가지: (1) 로그인한 사람(memberId)을 Dify user 식별자로 바꿔주는 것,
// (2) Dify 전용 응답(DifyChatResponseDTO)을 우리 API 응답(ChatMessageResponseDTO)으로 바꿔주는 것.
@Service
public class ChatService {

    private final DifyClient difyClient;
    private final ChatConversationOwnerStore conversationOwnerStore; //
    private final MemberMapper memberMapper;
    private final Clock clock;

    public ChatService(DifyClient difyClient, ChatConversationOwnerStore conversationOwnerStore, MemberMapper memberMapper, Clock clock) {
        this.difyClient = difyClient;
        this.conversationOwnerStore = conversationOwnerStore;
        this.memberMapper = memberMapper;
        this.clock = clock;
    }

    public ChatMessageResponseDTO sendMessage(MemberPrincipal principal, ChatMessageRequestDTO request) {
        Long memberId = principal.memberId();
        String conversationId = request.getConversationId();

        // conversationId가 왔다는 건 "이전 대화 이어가기"라는 뜻 - Dify에 보내기 전에
        // 우리 쪽에서 먼저 이 conversationId가 진짜 내 것인지 확인한다.
        // (클라이언트가 보낸 conversationId는 "주장"일 뿐이고, 이 Redis 조회가 그 주장을 검증하는 부분 -
        //  JwtProvider가 JWT의 서명을 검증하는 것과 같은 역할)
        if (conversationId != null) {
            Long ownerId = conversationOwnerStore.findOwner(conversationId);
            if (ownerId == null || !ownerId.equals(memberId)) {
                throw new BusinessException(ChatbotErrorCode.CHATBOT_CONVERSATION_FORBIDDEN);
            }


        }

        // memberId를 "member-{memberId}"로 매핑 - Dify 쪽에서 대화 소유자를 구분하기 위한 식별자.
        // (가족 구성원끼리 conversation_id가 안 섞이는 건 이 값이 아니라 위쪽 Redis 소유권 체크가 보장함 -
        //  이건 Dify 내부용 보조 식별자 역할만 함)

        String user = "member-" + memberId;

        // 나이 정보는 새 대화가 시작될 때(conversationId == null)만 붙인다. 이어지는 대화는
        // Dify가 이전 턴의 히스토리를 그대로 기억하고 있으므로, 매 턴 반복해서 보낼 필요가 없다.
        String query = conversationId == null
                ? withAgePrefix(memberId, request.getQuery())
                : request.getQuery();

        DifyChatResponseDTO response = difyClient.sendMessage(query, conversationId, user);

        // 매번(새 대화든 이어가는 대화든) 저장 - 이어가는 대화는 위에서 이미 소유자가 나임을 확인했으므로
        // 여기서 다시 저장하는 건 사실상 TTL 갱신 효과(계속 쓰는 대화가 세션 내내 안 만료되게).
        conversationOwnerStore.saveOwner(response.getConversationId(), memberId);

        return new ChatMessageResponseDTO(response.getAnswer(), response.getConversationId());
    }

    // "나는 {나이}살이야. {원래 질문}" 형태로 조립한다. 생년월일 조회가 안 되는 극단적
    // 상황이라면 나이 정보 없이 원래 질문만 그대로 보낸다 - 나이 힌트 하나 때문에
    // 챗봇 자체가 아예 실패하면 안 되므로 null-safe하게 뺀다.
    private String withAgePrefix(Long memberId, String query) {
        MemberVO member = memberMapper.selectById(memberId);
        if (member == null || member.getBirthDate() == null) {
            return query;
        }

        long age = ChronoUnit.YEARS.between(member.getBirthDate(), LocalDate.now(clock));
        return "나는 " + age + "살이야. " + query;
    }
}
