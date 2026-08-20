package com.teenyfin.teenymoney.global.sse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 진짜 Redis에 붙어 1회용 계약을 확인한다. Mock으로는 GETDEL의 원자성을 볼 수 없다.
 *
 * FamilyLinkCodeStoreRedisTest와 같은 방식으로 환경변수 게이팅한다. REDIS_HOST가 없으면
 * 통째로 스킵된다.
 *   REDIS_HOST=127.0.0.1 REDIS_PORT=6379 ./gradlew test
 *
 * 공유 서버(개발 EC2 등)를 향할 수 있으므로 두 가지를 지킨다.
 *   - KEYS를 쓰지 않는다. O(N)이라 서버 전체를 멈춰 세운다.
 *   - 이 테스트가 만든 키만 지운다. 티켓 값이 UUID라 실제 티켓과 겹칠 일도 없다.
 */
@EnabledIfEnvironmentVariable(named = "REDIS_HOST", matches = ".+")
class SseTicketStoreRedisTest {

    // 실제 회원 ID와 겹치지 않도록 충분히 큰 값을 쓴다.
    private static final Long MEMBER_ID = 999_000_001L;

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static SseTicketStore store;

    private static final List<String> issuedTickets = new ArrayList<>();

    @BeforeAll
    static void connect() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                System.getenv("REDIS_HOST"),
                Integer.parseInt(Objects.requireNonNullElse(System.getenv("REDIS_PORT"), "6379")));

        String password = System.getenv("REDIS_PASSWORD");
        if (password != null && !password.isBlank()) {
            configuration.setPassword(RedisPassword.of(password));
        }

        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        store = new SseTicketStore(redisTemplate);
    }

    @AfterAll
    static void cleanUp() {
        // 소비되지 않고 남은 티켓만 지운다. 30초 뒤 어차피 만료되지만 공유 서버를 더럽히지 않는다.
        for (String ticket : issuedTickets) {
            redisTemplate.delete("sse:ticket:" + ticket);
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    private String issue() {
        String ticket = store.issue(MEMBER_ID);
        issuedTickets.add(ticket);
        return ticket;
    }

    @Test
    @DisplayName("발급한 티켓을 소비하면 발급받은 회원이 나온다")
    void 발급하고_소비하면_회원이_나온다() {
        String ticket = issue();

        assertThat(store.consume(ticket)).isEqualTo(MEMBER_ID);
    }

    @Test
    @DisplayName("한 번 소비한 티켓은 다시 쓸 수 없다")
    void 티켓은_1회용이다() {
        String ticket = issue();

        assertThat(store.consume(ticket)).isEqualTo(MEMBER_ID);
        assertThat(store.consume(ticket)).isNull();
    }

    @Test
    @DisplayName("없는 티켓·빈 값은 조용히 null이다")
    void 없는_티켓은_null이다() {
        assertThat(store.consume("존재하지-않는-티켓")).isNull();
        assertThat(store.consume(null)).isNull();
        assertThat(store.consume("  ")).isNull();
    }

    @Test
    @DisplayName("TTL이 걸려 있다 - 안 쓰고 두면 알아서 사라진다")
    void 티켓에는_TTL이_있다() {
        String ticket = issue();

        Long ttlSeconds = redisTemplate.getExpire("sse:ticket:" + ticket, TimeUnit.SECONDS);

        assertThat(ttlSeconds).isNotNull();
        assertThat(ttlSeconds).isPositive();
        assertThat(ttlSeconds).isLessThanOrEqualTo(30);
    }

    @Test
    @DisplayName("같은 티켓으로 동시에 붙어도 성공하는 쪽은 하나뿐이다 - GETDEL의 원자성")
    void 동시_소비는_한_쪽만_성공한다() throws Exception {
        String ticket = issue();

        int racers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        CyclicBarrier barrier = new CyclicBarrier(racers);

        List<Callable<Long>> tasks = new ArrayList<>();
        for (int i = 0; i < racers; i++) {
            tasks.add(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return store.consume(ticket);
            });
        }

        List<Future<Long>> futures = pool.invokeAll(tasks);
        long winners = 0;
        for (Future<Long> future : futures) {
            if (future.get() != null) {
                winners++;
            }
        }
        pool.shutdownNow();

        assertThat(winners).isEqualTo(1);
    }
}
