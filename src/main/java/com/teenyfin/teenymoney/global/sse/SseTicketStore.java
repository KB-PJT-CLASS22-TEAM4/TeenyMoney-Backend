package com.teenyfin.teenymoney.global.sse;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * SSE 구독용 1회용 티켓.
 *
 * 브라우저 네이티브 EventSource는 Authorization 헤더를 붙일 수 없다. 그래서 구독은 쿼리
 * 파라미터로 인증하는데, 액세스 토큰을 거기 넣지 않는다 - 쿼리는 액세스 로그에 남는다.
 * 대신 이 단명 티켓을 넣는다. 로그에 남더라도 이미 소비됐거나 30초 뒤 사라진 값이다.
 * 쿼리 파라미터를 쓰기로 한 선택 자체가 이 설계에서 방어해야 할 지점이고, 티켓이 그 방어다.
 *
 * 소비는 GETDEL이라 조회와 삭제가 한 명령이다. 같은 티켓으로 두 번 붙으려 해도 값을 받는
 * 쪽은 하나뿐이다. FamilyLinkCodeStore.consumeCode와 같은 방식이라 새 개념이 아니다.
 *
 * @Component로 두는 이유: global 패키지는 RootConfig 스캔 대상이라 이것만으로 루트 컨텍스트에
 * 싱글턴으로 올라간다. RefreshTokenStore처럼 RedisConfig에 @Bean으로 등록할 수도 있지만,
 * 생성자에 주입할 설정값이 없어서 등록 코드가 순수한 중복이 된다(FamilyLinkCodeStore와 같은 판단).
 */
@Component
public class SseTicketStore {

    private static final String KEY_PREFIX = "sse:ticket:";

    /**
     * 티켓 발급 직후 바로 구독하는 흐름이라 짧아도 된다. 짧을수록 로그에 남은 값의 수명이 짧다.
     */
    private static final Duration TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redisTemplate;

    public SseTicketStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 발급. 티켓 값 자체에는 의미가 없고 Redis에 있느냐만이 유효성의 근거다. */
    public String issue(Long memberId) {
        String ticket = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(key(ticket), memberId.toString(), TTL);
        return ticket;
    }

    /**
     * 읽으면서 지운다(GETDEL, Redis 6.2+).
     *
     * null은 셋 중 하나다 - 없는 티켓, 이미 소비된 티켓, 만료된 티켓.
     * 호출측에서 셋을 구분할 이유가 없으므로(전부 401) 구분하지 않는다.
     */
    public Long consume(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return null;
        }
        String memberId = redisTemplate.opsForValue().getAndDelete(key(ticket));

        return memberId == null ? null : Long.valueOf(memberId);
    }

    private String key(String ticket) {
        return KEY_PREFIX + ticket;
    }
}
