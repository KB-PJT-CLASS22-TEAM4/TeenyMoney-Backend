package com.teenyfin.teenymoney.domain.family.store;

import com.teenyfin.teenymoney.domain.family.service.FamilyLinkCodeService;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 진짜 Redis에 붙어 동시성 계약을 확인한다. Mock으로는 명령 사이의 경쟁을 만들 수 없다.
 *
 * DB 통합 테스트와 같은 방식으로 환경변수 게이팅한다. REDIS_HOST가 없으면 통째로 스킵된다.
 *   REDIS_HOST=127.0.0.1 REDIS_PORT=6379 ./gradlew test
 *
 * 공유 서버(개발 EC2 등)를 향할 수 있으므로 두 가지를 지킨다.
 *   - KEYS를 쓰지 않는다. O(N)이라 서버 전체를 멈춰 세운다.
 *   - 이 테스트가 만든 키만 지운다. family-link:* 를 통째로 지우면 실제 사용자의
 *     유효한 연동 코드가 사라진다.
 * 테스트가 뽑는 코드가 실제 코드와 우연히 겹쳐도 안전하다. 스크립트가 EXISTS에서
 * 빠져나오며 아무것도 쓰지 않기 때문이다(그 경우 발급이 재시도된다).
 */
@EnabledIfEnvironmentVariable(named = "REDIS_HOST", matches = ".+")
class FamilyLinkCodeStoreRedisTest {

    // 실제 회원 ID와 겹치지 않도록 충분히 큰 값을 쓴다.
    private static MemberMapper memberMapper;
    private static final Long PARENT_ID = 99000017L;
    private static final Long OTHER_PARENT_ID = 99000042L;
    private static final Duration TTL = Duration.ofMinutes(10);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static FamilyLinkCodeStore store;
    private static FamilyLinkCodeService service;

    /** 이 테스트가 만든 코드. 정리 대상을 이름으로 정확히 안다. */
    private final List<String> createdCodes = new ArrayList<>();

    /** "{parentId}:{key}" 형태. 멱등 키도 남기지 않고 회수한다. */
    private final List<String> usedIdempotencyKeys = new ArrayList<>();

    @BeforeAll
    static void connect() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                System.getenv().getOrDefault("REDIS_HOST", "localhost"),
                Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379")));
        configuration.setPassword(RedisPassword.of(
                System.getenv().getOrDefault("REDIS_PASSWORD", "")));

        // 운영과 같은 서버를 볼 수 있으므로 논리 DB로 격리한다.
        // 앱(RedisConfig)은 setDatabase를 호출하지 않아 db 0을 쓴다. 테스트는 그 옆방을 쓴다.
        int database = Integer.parseInt(System.getenv().getOrDefault("REDIS_TEST_DB", "1"));
        if (database == 0) {
            throw new IllegalStateException(
                    "REDIS_TEST_DB=0은 앱이 쓰는 DB다. 다른 인덱스를 지정할 것.");
        }
        configuration.setDatabase(database);
        System.out.println("### 통합 테스트 대상 Redis db = " + database);

        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        store = new FamilyLinkCodeStore(redisTemplate);
        service = new FamilyLinkCodeService(store, memberMapper, Clock.system(ZoneId.of("Asia/Seoul")));
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    @AfterEach
    void clean() {
        List<String> keys = new ArrayList<>();
        for (Long parentId : List.of(PARENT_ID, OTHER_PARENT_ID)) {
            // 슬롯이 가리키는 코드까지 회수한다(테스트 중 새로 발급된 것 포함).
            String current = redisTemplate.opsForValue().get("family-link:parent:" + parentId);
            if (current != null) {
                keys.add("family-link:code:" + current);
            }
            keys.add("family-link:parent:" + parentId);
            keys.add("family-link:cooldown:" + parentId);
        }
        createdCodes.forEach(code -> keys.add("family-link:code:" + code));
        createdCodes.clear();
        usedIdempotencyKeys.forEach(k -> keys.add("family-link:idem:" + k));
        usedIdempotencyKeys.clear();

        redisTemplate.delete(keys);
    }

    /** 매번 새 의도로 발급하고, 만든 키를 정리 목록에 기록한다. */
    private String issue(Long parentId) {
        return issue(parentId, UUID.randomUUID().toString());
    }

    private String issue(Long parentId, String idempotencyKey) {
        usedIdempotencyKeys.add(parentId + ":" + idempotencyKey);
        String code = service.makeCode(parentId, idempotencyKey).code();
        createdCodes.add(code);
        return code;
    }

    /** 쿨다운은 5초라, 재발급을 연달아 보려면 자물쇠를 직접 풀어야 한다. */
    private void releaseCooldown(Long parentId) {
        redisTemplate.delete("family-link:cooldown:" + parentId);
    }

    @Test
    @DisplayName("재발급하면 직전 코드는 소비할 수 없다")
    void reissueKillsPreviousCode() {
        String first = issue(PARENT_ID);
        releaseCooldown(PARENT_ID);
        String second = issue(PARENT_ID);

        assertNotEquals(first, second);

        assertThrows(BusinessException.class, () -> service.consumeCode(first));
        assertEquals(PARENT_ID, service.consumeCode(second));
    }

    @Test
    @DisplayName("동시 발급 요청 중 정확히 하나만 코드를 받는다")
    void onlyOneConcurrentIssueSucceeds() throws Exception {
        int threads = 8;

        // 서로 다른 의도(= 다른 멱등 키)로 동시에 들어온 요청이어야 쿨다운을 검증할 수 있다.
        List<String> issued = runConcurrently(threads, () -> {
            try {
                String key = UUID.randomUUID().toString();
                usedIdempotencyKeys.add(PARENT_ID + ":" + key);
                return service.makeCode(PARENT_ID, key).code();
            } catch (BusinessException e) {
                // 나머지는 FAMILY_LINK_CODE_TOO_SOON. 이게 상호배제의 증거다.
                assertEquals("FAMILY_LINK_CODE_TOO_SOON", e.getErrorCode().getCode());
                return null;
            }
        }).stream().filter(Objects::nonNull).collect(Collectors.toList());

        assertEquals(1, issued.size(), "동시 요청이 여러 코드를 발급했다: " + issued);
        createdCodes.addAll(issued);

        // 슬롯이 그 하나를 가리켜야 한다. KEYS 스캔 없이 확인한다.
        assertEquals(
                issued.get(0),
                redisTemplate.opsForValue().get("family-link:parent:" + PARENT_ID));
    }

    @Test
    @DisplayName("같은 코드를 동시에 소비하면 한 요청만 부모 ID를 받는다")
    void onlyOneConcurrentConsumeSucceeds() throws Exception {
        String code = issue(PARENT_ID);
        int threads = 8;

        long consumed = runConcurrently(threads, () -> store.consumeCode(code))
                .stream().filter(Objects::nonNull).count();

        assertEquals(1, consumed);
    }

    @Test
    @DisplayName("코드가 충돌하면 발급이 거절되고, 그 코드의 진짜 주인은 영향을 받지 않는다")
    void collisionNeverTouchesTheOwnersCode() {
        String othersCode = issue(OTHER_PARENT_ID);

        // PARENT_ID가 우연히 같은 코드를 뽑은 상황
        assertNull(store.tryIssueCode(PARENT_ID, othersCode, UUID.randomUUID().toString(), TTL));

        assertEquals(OTHER_PARENT_ID, service.consumeCode(othersCode),
                "다른 부모의 유효한 코드가 훼손됐다");
    }

    @Test
    @DisplayName("충돌로 발급이 거절돼도 내 이전 코드는 살아남는다")
    void collisionKeepsMyPreviousCodeAlive() {
        String othersCode = issue(OTHER_PARENT_ID);
        String mine = issue(PARENT_ID);

        // 재발급을 시도했지만 남이 쓰는 코드라 거절됐다.
        assertNull(store.tryIssueCode(PARENT_ID, othersCode, UUID.randomUUID().toString(), TTL));

        // 스크립트가 EXISTS에서 아무것도 쓰지 않고 빠져나오므로 내 코드는 그대로여야 한다.
        assertEquals(PARENT_ID, service.consumeCode(mine));
    }

    @Test
    @DisplayName("소비된 코드는 다시 쓸 수 없다")
    void consumedCodeCannotBeReused() {
        String code = issue(PARENT_ID);

        assertEquals(PARENT_ID, store.consumeCode(code));
        assertNull(store.consumeCode(code));
    }

    @Test
    @DisplayName("소비로 죽은 코드를 다른 부모가 다시 잡았을 때, 원래 부모의 재발급이 그것을 지우지 않는다")
    void reissueNeverDeletesARecycledCodeNowOwnedByAnotherParent() {
        // consumeCode는 코드 키만 지우고 슬롯은 남긴다. 그래서 슬롯이 죽은 코드를 가리킨다.
        String recycled = issue(PARENT_ID);
        assertEquals(PARENT_ID, store.consumeCode(recycled));
        assertEquals(recycled,
                redisTemplate.opsForValue().get("family-link:parent:" + PARENT_ID),
                "전제가 깨졌다 - 슬롯이 소비된 코드를 가리키지 않는다");

        // 다른 부모가 우연히 같은 번호를 뽑아 정상 예약한다.
        assertEquals(recycled,
                store.tryIssueCode(OTHER_PARENT_ID, recycled, UUID.randomUUID().toString(), TTL));
        createdCodes.add(recycled);

        // 원래 부모가 재발급한다. 슬롯에는 아직 recycled가 적혀 있다.
        releaseCooldown(PARENT_ID);
        issue(PARENT_ID);

        assertEquals(OTHER_PARENT_ID, store.consumeCode(recycled),
                "다른 부모의 유효한 코드가 삭제됐다");
    }

    @Test
    @DisplayName("쿨다운이 만료된 뒤 같은 멱등 키로 재시도해도 같은 코드를 받는다")
    void lateRetryWithSameKeyGetsTheSameCodeEvenAfterCooldownExpires() {
        String key = "intent-" + UUID.randomUUID();

        // 요청 A: 코드를 발급받았지만 응답이 지연되는 중이라고 하자.
        String codeFromA = issue(PARENT_ID, key);

        // 쿨다운(5초)이 만료된 상황을 만든다. 여기서부터가 원래 뚫리던 구간이다.
        releaseCooldown(PARENT_ID);

        // 요청 B: 같은 의도의 타임아웃 재시도. 새 코드를 만들면 A의 코드가 죽는다.
        String codeFromB = issue(PARENT_ID, key);

        assertEquals(codeFromA, codeFromB,
                "같은 멱등 키인데 새 코드가 발급됐다 - 늦게 도착한 A 응답이 죽은 코드를 남긴다");

        // A가 들고 있는 코드가 여전히 유효해야 한다.
        assertEquals(PARENT_ID, service.consumeCode(codeFromA));
    }

    @Test
    @DisplayName("다른 멱등 키로 재발급하면 이전 코드는 무효가 된다")
    void differentKeyStillInvalidatesThePreviousCode() {
        String first = issue(PARENT_ID, "intent-a-" + UUID.randomUUID());
        releaseCooldown(PARENT_ID);
        String second = issue(PARENT_ID, "intent-b-" + UUID.randomUUID());

        assertNotEquals(first, second);
        assertThrows(BusinessException.class, () -> service.consumeCode(first));
        assertEquals(PARENT_ID, service.consumeCode(second));
    }

    @Test
    @DisplayName("스크립트 자체가 멱등하다 - 쿨다운을 거치지 않고 같은 키로 동시 호출해도 코드는 하나다")
    void scriptItselfIsIdempotentUnderConcurrency() throws Exception {
        // service를 거치면 쿨다운이 먼저 걸러내 스크립트의 멱등 처리가 가려진다.
        // 여기서는 store를 직접 때려서 스크립트만 검증한다.
        String key = "intent-" + UUID.randomUUID();
        usedIdempotencyKeys.add(PARENT_ID + ":" + key);
        int threads = 8;

        List<String> codes = runConcurrently(threads, () -> {
            // 스레드마다 다른 후보 코드를 넘긴다. 멱등하다면 전부 같은 값이 돌아와야 한다.
            String candidate = String.format(
                    "%06d", 900000 + Thread.currentThread().getId() % 90000);
            return store.tryIssueCode(PARENT_ID, candidate, key, TTL);
        }).stream().filter(Objects::nonNull).collect(Collectors.toList());

        createdCodes.addAll(codes);

        assertEquals(threads, codes.size(), "일부 호출이 충돌로 실패했다: " + codes);
        assertEquals(1, codes.stream().distinct().count(),
                "같은 멱등 키인데 서로 다른 코드가 나왔다: " + codes.stream().distinct().toList());
    }

    @Test
    @DisplayName("같은 멱등 키로 동시에 들어와도 코드는 하나만 만들어진다")
    void concurrentRetriesWithTheSameKeyProduceOneCode() throws Exception {
        String key = "intent-" + UUID.randomUUID();
        usedIdempotencyKeys.add(PARENT_ID + ":" + key);
        int threads = 8;

        List<String> codes = runConcurrently(threads, () -> {
            try {
                return service.makeCode(PARENT_ID, key).code();
            } catch (BusinessException e) {
                assertEquals("FAMILY_LINK_CODE_TOO_SOON", e.getErrorCode().getCode());
                return null;
            }
        }).stream().filter(Objects::nonNull).collect(Collectors.toList());

        createdCodes.addAll(codes);
        assertEquals(1, codes.stream().distinct().count(),
                "같은 멱등 키인데 서로 다른 코드가 나왔다: " + codes);
    }

    @Test
    @DisplayName("코드가 소비된 뒤에도 재발급은 정상적으로 새 코드를 준다")
    void reissueWorksAfterConsumption() {
        String first = issue(PARENT_ID);
        assertEquals(PARENT_ID, store.consumeCode(first));

        releaseCooldown(PARENT_ID);
        String second = issue(PARENT_ID);

        assertNotEquals(first, second);
        assertEquals(second,
                redisTemplate.opsForValue().get("family-link:parent:" + PARENT_ID));
        assertEquals(PARENT_ID, store.consumeCode(second));
    }

    /** 모든 스레드를 같은 순간에 출발시켜야 경쟁이 실제로 일어난다. */
    private <T> List<T> runConcurrently(int threads, Callable<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier start = new CyclicBarrier(threads);
        try {
            List<Future<T>> futures = IntStream.range(0, threads)
                    .mapToObj(i -> pool.submit(() -> {
                        start.await(5, TimeUnit.SECONDS);
                        return task.call();
                    }))
                    .collect(Collectors.toList());

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
