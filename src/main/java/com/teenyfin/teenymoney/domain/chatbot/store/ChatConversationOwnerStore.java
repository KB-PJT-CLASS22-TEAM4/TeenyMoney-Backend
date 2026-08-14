package com.teenyfin.teenymoney.domain.chatbot.store;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

// Dify conversationId가 "누구 것인지"만 기억하는 저장소.
// 대화 내용(질문/답변 텍스트)은 여전히 저장하지 않는다 - 딱 ID 하나(conversationId -> memberId)만 남긴다.
// TTL은 세션 스케일(분 단위)로 짧게 잡는다 - "챗봇 켜져있는 동안만 이어지고, 끄면 끝"이라는
// 제품 의도랑 저장 수명을 맞추기 위함. 메시지 보낼 때마다 saveOwner()가 TTL을 새로 걸기 때문에
// (sliding expiration), 활발히 대화 중인 세션은 안 끊기고 방치된 대화만 자연 소멸한다.
@Component // @Component라서 RedisConfig에 수동으로 @Bean 등록 안 해도 됨 - PhoneVerificationStore/FamilyLinkCodeStore와 동일 패턴
public class ChatConversationOwnerStore {

    // Redis 키 접두사. 실제 키는 "chatbot:conversation-owner:{conversationId}" 형태가 됨
    private static final String OWNER_PREFIX = "chatbot:conversation-owner:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ownerTtl;

    // ownerTtlMinutes: application.properties의 dify.conversation-owner-ttl-minutes를 주입받음
    public ChatConversationOwnerStore(StringRedisTemplate redisTemplate, @Value("${dify.conversation-owner-ttl-minutes}") long ownerTtlMinutes) {
        this.redisTemplate = redisTemplate;
        this.ownerTtl = Duration.ofMinutes(ownerTtlMinutes);
    }

    // 대화가 새로 시작되거나 이어질 때마다 호출한다.
    // set()이 매번 TTL을 새로 덮어쓰기 때문에, 이 메서드를 부를 때마다 만료 시각이
    // "지금부터 ownerTtl 뒤"로 갱신된다 - 즉 계속 대화 중이면 절대 안 끊긴다.
    public void saveOwner(String conversationId, Long memberId) {
        redisTemplate.opsForValue().set(ownerKey(conversationId), memberId.toString(), ownerTtl);
    }

    // 저장된 적 없거나 TTL이 지나서 사라졌으면 null.
    // null이 "소유자가 나다/아니다"를 뜻하는 게 아니라 "모른다"는 뜻이므로,
    // 호출하는 쪽(ChatService)에서 null도 "내 것이 아님"으로 취급해서 막아야 한다.
    public Long findOwner(String conversationId) {
        String memberId = redisTemplate.opsForValue().get(ownerKey(conversationId));
        return memberId == null ? null : Long.valueOf(memberId);
    }

    private String ownerKey(String conversationId) {
        return OWNER_PREFIX + conversationId;
    }
}
