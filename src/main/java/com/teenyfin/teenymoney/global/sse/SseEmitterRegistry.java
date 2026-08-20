package com.teenyfin.teenymoney.global.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 열려 있는 SSE 연결을 회원별로 들고 있다가, 상태가 바뀌면 신호를 흘린다.
 *
 * RootConfig가 스캔하는 싱글턴이다(@Component, global 패키지). 에미터를 JVM 메모리에
 * 들고 있어도 되는 이유는 단일 EC2·단일 Tomcat이기 때문이다. 인스턴스가 늘어나는 날
 * 깨지는 것은 이 클래스 하나뿐이고, 그때 앞에 Redis pub/sub을 얹으면 된다
 * (티켓은 이미 Redis라 그대로 동작한다).
 *
 * 값이 컬렉션인 이유: 회원 한 명이 탭을 여러 개 열 수 있다.
 * CopyOnWriteArraySet인 이유: 쓰기(연결·해제)는 드물고 읽기(발행 시 순회)가 잦다.
 */
@Slf4j
@Component
public class SseEmitterRegistry {

    private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * 하트비트 전용 스레드.
     *
     * @Scheduled를 쓰지 않는다. RootConfig에 TaskScheduler 빈이 없어 기본 풀 크기가 1이고,
     * 그 한 스레드를 AllowanceScheduler·FinancialProductMaturityScheduler 등 5개 배치가
     * 공유한다(둘은 심지어 같은 시각 0 10 0 * * * 이다). 하트비트를 거기 얹으면 야간 배치가
     * 도는 동안 :ping이 한 번도 안 나가고, 프록시 idle timeout에 전 연결이 죽는다.
     * 반대로 하트비트가 죽은 소켓 쓰기에서 블록되면 배치가 밀린다.
     *
     * 하트비트는 SSE의 인프라이지 애플리케이션 배치가 아니다. 자기 스레드를 갖는다.
     */
    private final ScheduledExecutorService heartbeatScheduler =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "sse-heartbeat");
                // 데몬으로 둬야 종료 시 이 스레드가 JVM을 붙잡지 않는다.
                thread.setDaemon(true);
                return thread;
            });

    private final long emitterTimeoutMs;
    private final long heartbeatIntervalMs;

    /**
     * 두 값 모두 측정하지 않고 정했다. 그래서 상수로 박지 않고 프로퍼티로 뺀다.
     *
     * heartbeat 25초: 로컬 Vite 프록시가 idle 60초(vite.config.js), nginx 기본값도 60초라
     * 2배 여유를 뒀다. 프로덕션 앞단 구성은 확인하지 못했다(docs/DEPLOY.md에 기록이 없다).
     * emitter timeout 30분: 기본값에 맡기면 컨테이너 설정에 따라 동작이 달라져서 명시한다.
     */
    public SseEmitterRegistry(
            @Value("${sse.emitter-timeout-ms:1800000}") long emitterTimeoutMs,
            @Value("${sse.heartbeat-interval-ms:25000}") long heartbeatIntervalMs) {
        this.emitterTimeoutMs = emitterTimeoutMs;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    @PostConstruct
    void startHeartbeat() {
        heartbeatScheduler.scheduleAtFixedRate(
                this::heartbeat, heartbeatIntervalMs, heartbeatIntervalMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stopHeartbeat() {
        heartbeatScheduler.shutdownNow();
    }

    /**
     * 연결을 등록하고 에미터를 돌려준다. 컨트롤러가 이걸 그대로 반환하면 Servlet 3.0 async로
     * 응답이 열린 채 유지된다.
     *
     * 세 콜백에서 반드시 자기 자신을 제거한다. 이걸 빠뜨리면 죽은 에미터가 무한히 쌓인다.
     * 이 클래스에서 실제로 터질 수 있는 버그는 여기다(SseEmitterRegistryTest가 검증한다).
     */
    public SseEmitter add(Long memberId) {
        SseEmitter emitter = createEmitter();

        emitter.onCompletion(() -> remove(memberId, emitter));
        emitter.onTimeout(() -> remove(memberId, emitter));
        emitter.onError(throwable -> remove(memberId, emitter));

        emitters.compute(memberId, (id, existing) -> {
            Set<SseEmitter> target = (existing == null) ? new CopyOnWriteArraySet<>() : existing;
            target.add(emitter);
            return target;
        });

        return emitter;
    }

    /**
     * 에미터 생성 이음매.
     *
     * SseEmitter는 서블릿 응답에 붙기 전에는 send()가 조용히 버퍼링만 하고 실패하지 않아서,
     * 단위 테스트에서 "전송 실패 시 정리" 경로를 만들 수가 없다. 테스트가 실패하는 에미터를
     * 끼워 넣을 수 있도록 생성만 떼어 둔다. 프로덕션 동작은 바뀌지 않는다.
     */
    SseEmitter createEmitter() {
        return new SseEmitter(emitterTimeoutMs);
    }

    /**
     * 마지막 에미터가 빠지면 회원 키까지 지운다. compute 안에서 하므로 "비었는지 확인 후 삭제"
     * 사이에 다른 스레드가 끼어들 창이 없다. 남겨두면 로그아웃한 회원의 빈 Set이 계속 쌓인다.
     *
     * 패키지 밖으로 열지 않는다. 바깥에서 부를 일이 없고, 테스트만 직접 호출한다 -
     * 컨테이너가 부르는 onCompletion/onTimeout/onError 콜백을 단위 테스트에서 흉내 낼 수 없어서다.
     */
    void remove(Long memberId, SseEmitter emitter) {
        emitters.compute(memberId, (id, existing) -> {
            if (existing == null) {
                return null;
            }
            existing.remove(emitter);
            return existing.isEmpty() ? null : existing;
        });
    }

    /**
     * best-effort다. 전송 실패는 로그만 남기고 해당 에미터를 제거하며, 절대 예외를 위로
     * 던지지 않는다. 화면 갱신 신호를 못 보낸 것 때문에 송금이나 퀘스트 생성이 실패하면 안 된다
     * (TransferService.notifyAllowanceRecipientBestEffort와 같은 원칙).
     */
    public void send(Long memberId, String eventName) {
        Set<SseEmitter> targets = emitters.get(memberId);
        if (targets == null) {
            return;
        }
        for (SseEmitter emitter : targets) {
            doSend(memberId, emitter, SseEmitter.event().name(eventName).data("{}"));
        }
    }

    /**
     * 하트비트는 두 가지를 동시에 한다.
     *   - 중간 프록시의 idle timeout으로 연결이 끊기는 것을 막는다
     *   - 사라진 클라이언트를 전송 실패로 감지해 정리한다(서버는 쓰기 전까지 죽은 걸 모른다)
     *
     * SSE 주석 라인(":ping")이라 EventSource의 이벤트로는 올라가지 않는다.
     *
     * 전체를 try로 감싸는 이유: scheduleAtFixedRate는 태스크가 예외를 던지면 그 뒤로
     * 영영 실행하지 않는다. 한 번 새면 모든 연결이 다음 프록시 timeout에 조용히 죽는다.
     */
    void heartbeat() {
        try {
            emitters.forEach((memberId, targets) ->
                    targets.forEach(emitter ->
                            doSend(memberId, emitter, SseEmitter.event().comment("ping"))));
        } catch (RuntimeException e) {
            log.warn("SSE 하트비트 중 예외", e);
        }
    }

    /**
     * 상태 변경을 받아 신호로 바꾼다.
     *
     * AFTER_COMMIT인 이유: 커밋 전에 쏘면 클라이언트가 곧바로 조회 API를 호출하고, 그 조회는
     * 아직 커밋되지 않은 데이터를 보지 못한다. 화면이 갱신 안 된 채로 남고 재현이 어려운
     * 타이밍 버그가 된다. 롤백되면 이 메서드가 아예 실행되지 않는 것도 덤으로 따라온다.
     *
     * fallbackExecution = true가 여기서 가장 중요하다. 이게 없으면 활성 트랜잭션이 없을 때
     * 스프링이 예외도 로그도 없이 리스너를 통째로 건너뛴다. 실제로 트랜잭션 밖에서 발행되는
     * 지점이 있다 - TransferService.executeTransfer는 @Transactional(NOT_SUPPORTED)이고,
     * QuestProgressService.submitVerification은 S3 업로드 때문에 transactionTemplate을 쓴다.
     * 그 자리들은 이미 데이터가 커밋된 뒤라 즉시 발행하는 것이 맞다.
     *
     * 즉 규칙은 "커밋 이후"가 아니라 "데이터가 보이게 된 이후"이고, 이 한 줄이 둘 다 만족시킨다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onStateChanged(SseEvent event) {
        if (event.memberId() == null || event.type() == null) {
            return;
        }
        send(event.memberId(), event.type().name());
    }

    /** 회원의 열린 연결 수. 누수 검증용이라 패키지 밖으로 열지 않는다. */
    int connectionCount(Long memberId) {
        Set<SseEmitter> targets = emitters.get(memberId);
        return targets == null ? 0 : targets.size();
    }

    /** 맵 자체가 비었는지. 마지막 연결이 끊기면 회원 키까지 사라져야 한다. */
    boolean isEmpty() {
        return emitters.isEmpty();
    }

    private void doSend(Long memberId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            // ponytail: 에미터 단위 락. 전역 락이 아니라 연결 하나만 직렬화한다.
            // SseEmitter.send()가 스레드 안전하지 않아서, 하트비트 스레드와 이벤트 스레드가
            // 같은 에미터에 동시에 쓰면 스트림이 섞이거나 IllegalStateException이 난다.
            // 연결 수가 늘어 락 경합이 보이면 그때 회원별 큐 + 단일 소비자로 바꾼다.
            synchronized (emitter) {
                emitter.send(event);
            }
        } catch (Exception e) {
            // 클라이언트가 사라졌다는 뜻이다. 정상 경로이므로 debug로 남긴다.
            log.debug("SSE 전송 실패 - 에미터 제거 memberId={}", memberId, e);
            remove(memberId, emitter);
            completeQuietly(emitter, e);
        }
    }

    /**
     * 끊긴 연결의 async 요청을 컨테이너가 놓아주게 한다. 이게 없으면 죽은 요청이
     * 에미터 타임아웃(기본 30분)까지 서블릿 컨테이너에 붙어 있는다.
     * 이미 완료된 에미터면 스프링이 예외를 던질 수 있어 통째로 삼킨다.
     */
    private void completeQuietly(SseEmitter emitter, Exception cause) {
        try {
            emitter.completeWithError(cause);
        } catch (Exception ignored) {
            // 이미 끝난 에미터. 할 일이 없다.
        }
    }
}
