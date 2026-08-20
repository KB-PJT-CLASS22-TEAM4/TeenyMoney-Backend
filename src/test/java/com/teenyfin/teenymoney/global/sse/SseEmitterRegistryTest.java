package com.teenyfin.teenymoney.global.sse;

import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 순수 단위 테스트. Redis도 DB도 스프링 컨텍스트도 필요 없다.
 *
 * 이 클래스에서 실제로 터질 수 있는 버그는 '에미터 누수'다. 연결이 끊겼는데 맵에서 안 빠지면
 * 죽은 에미터가 무한히 쌓이고, 하트비트가 매번 그걸 전부 순회하며 실패한다. 그래서 아래
 * 테스트의 절반이 "끊긴 뒤에 맵이 비었는가"를 본다.
 *
 * 한계를 밝혀 둔다. 서블릿 응답에 붙지 않은 SseEmitter는
 *   - send()가 실패하지 않고 조용히 버퍼링만 하고
 *   - complete()가 onCompletion 콜백을 부르지 않는다 (콜백은 컨테이너 핸들러가 부른다)
 * 그래서 여기서는 콜백 배선 자체가 아니라, 콜백이 부르는 remove()의 장부 관리와
 * 전송 실패 경로를 검증한다. 실제 배선은 수동 검증(자녀 탭을 닫고 서버 로그 확인)이 담당한다.
 */
class SseEmitterRegistryTest {

    /** 하트비트가 테스트 도중 끼어들지 않도록 간격을 아주 길게 준다(@PostConstruct도 안 부른다). */
    private SseEmitterRegistry registry() {
        return new SseEmitterRegistry(30_000L, 600_000L);
    }

    /** 보낸 것을 기록만 하는 에미터. 진짜 응답이 없어도 전달 여부를 볼 수 있다. */
    private static class RecordingEmitter extends SseEmitter {
        final List<Object> sent = new CopyOnWriteArrayList<>();

        @Override
        public void send(SseEventBuilder builder) {
            sent.add(builder);
        }
    }

    /** 클라이언트가 사라진 상태를 흉내 낸다. */
    private static class FailingEmitter extends SseEmitter {
        @Override
        public void send(SseEventBuilder builder) throws IOException {
            throw new IOException("클라이언트가 사라졌다");
        }
    }

    private SseEmitterRegistry registryEmitting(SseEmitter... emitters) {
        return new SseEmitterRegistry(30_000L, 600_000L) {
            private int index = 0;

            @Override
            SseEmitter createEmitter() {
                return emitters[index++];
            }
        };
    }

    @Test
    @DisplayName("등록하면 회원의 연결로 잡히고, 이벤트가 그 연결로 나간다")
    void 등록하면_이벤트가_전달된다() {
        RecordingEmitter emitter = new RecordingEmitter();
        SseEmitterRegistry registry = registryEmitting(emitter);

        registry.add(1L);
        assertThat(registry.connectionCount(1L)).isEqualTo(1);

        registry.send(1L, NotificationReferenceType.QUEST.name());

        assertThat(emitter.sent).hasSize(1);
    }

    @Test
    @DisplayName("한 회원이 탭을 여러 개 열면 모든 탭으로 나간다")
    void 멀티탭_모두에_전달된다() {
        RecordingEmitter tab1 = new RecordingEmitter();
        RecordingEmitter tab2 = new RecordingEmitter();
        SseEmitterRegistry registry = registryEmitting(tab1, tab2);

        registry.add(1L);
        registry.add(1L);

        assertThat(registry.connectionCount(1L)).isEqualTo(2);

        registry.send(1L, NotificationReferenceType.QUEST.name());

        assertThat(tab1.sent).hasSize(1);
        assertThat(tab2.sent).hasSize(1);
    }

    @Test
    @DisplayName("다른 회원에게는 새지 않는다")
    void 다른_회원에게는_가지_않는다() {
        RecordingEmitter mine = new RecordingEmitter();
        RecordingEmitter other = new RecordingEmitter();
        SseEmitterRegistry registry = registryEmitting(mine, other);

        registry.add(1L);
        registry.add(2L);

        registry.send(1L, NotificationReferenceType.TRANSFER.name());

        assertThat(mine.sent).hasSize(1);
        assertThat(other.sent).isEmpty();
    }

    @Test
    @DisplayName("연결이 빠지면 맵에서 지워지고, 마지막이면 회원 키까지 사라진다")
    void 마지막_연결이_빠지면_회원_키까지_지운다() {
        RecordingEmitter first = new RecordingEmitter();
        RecordingEmitter second = new RecordingEmitter();
        SseEmitterRegistry registry = registryEmitting(first, second);

        registry.add(1L);
        registry.add(1L);

        registry.remove(1L, first);
        assertThat(registry.connectionCount(1L)).isEqualTo(1);

        registry.remove(1L, second);
        assertThat(registry.connectionCount(1L)).isZero();

        // 빈 Set만 남기면 로그아웃한 회원의 껍데기가 계속 쌓인다. 키까지 사라져야 한다.
        assertThat(registry.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("전송이 실패하면 그 에미터를 스스로 제거한다")
    void 전송_실패한_에미터는_제거된다() {
        SseEmitterRegistry registry = registryEmitting(new FailingEmitter());

        registry.add(1L);
        assertThat(registry.connectionCount(1L)).isEqualTo(1);

        registry.send(1L, NotificationReferenceType.TRANSFER.name());

        assertThat(registry.connectionCount(1L)).isZero();
        assertThat(registry.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("살아 있는 연결은 옆 연결이 죽어도 함께 지워지지 않는다")
    void 죽은_연결만_골라_제거한다() {
        RecordingEmitter alive = new RecordingEmitter();
        SseEmitterRegistry registry = registryEmitting(new FailingEmitter(), alive);

        registry.add(1L);
        registry.add(1L);

        registry.send(1L, NotificationReferenceType.QUEST.name());

        assertThat(registry.connectionCount(1L)).isEqualTo(1);
        assertThat(alive.sent).hasSize(1);
    }

    @Test
    @DisplayName("하트비트가 죽은 연결을 정리한다 - 서버는 쓰기 전까지 끊긴 걸 모른다")
    void 하트비트가_죽은_연결을_정리한다() {
        RecordingEmitter alive = new RecordingEmitter();
        SseEmitterRegistry registry = registryEmitting(new FailingEmitter(), alive);

        registry.add(1L);
        registry.add(2L);

        registry.heartbeat();

        assertThat(registry.connectionCount(1L)).isZero();
        assertThat(registry.connectionCount(2L)).isEqualTo(1);
        assertThat(alive.sent).hasSize(1);   // 살아 있는 쪽엔 :ping이 갔다
    }

    @Test
    @DisplayName("상태 변경 이벤트가 이벤트 이름 그대로 흘러간다")
    void 이벤트_이름은_referenceType_그대로다() {
        RecordingEmitter emitter = new RecordingEmitter();
        SseEmitterRegistry registry = registryEmitting(emitter);
        registry.add(1L);

        registry.onStateChanged(new SseEvent(1L, NotificationReferenceType.TODAY_PERMISSION));

        assertThat(emitter.sent).hasSize(1);
    }

    @Test
    @DisplayName("발행은 best-effort다 - 실패해도 예외가 호출측으로 새지 않는다")
    void 발행은_예외를_던지지_않는다() {
        SseEmitterRegistry registry = registryEmitting(new FailingEmitter());
        registry.add(1L);

        // 화면 갱신 신호를 못 보낸 것 때문에 송금이나 퀘스트 생성이 실패하면 안 된다.
        registry.onStateChanged(new SseEvent(1L, NotificationReferenceType.QUEST));
        registry.send(999L, "QUEST");                        // 연결이 하나도 없는 회원
        registry.onStateChanged(new SseEvent(null, null));   // 방어 대상
        registry.heartbeat();
    }

    @Test
    @DisplayName("여러 스레드가 같이 붙었다 끊어져도 맵에 껍데기가 남지 않는다")
    void 동시_등록_해제에도_누수가_없다() throws Exception {
        SseEmitterRegistry registry = registry();
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            long memberId = i % 4;
            pool.submit(() -> {
                try {
                    start.await();
                    SseEmitter emitter = registry.add(memberId);
                    registry.remove(memberId, emitter);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(registry.isEmpty()).isTrue();
    }
}
