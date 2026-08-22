package com.teenyfin.teenymoney.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.ThreadPoolExecutor;

// Dify(챗봇 + 리포트 분석) 호출 전용 인프라 설정.
// (readTimeout 120초로), 기존 restTemplate와 같은 설정을 공유한다"는 사실 자체가 문제였다
// 하나만(예: 스레드풀 작업 때 Dify 쪽 타임아웃) 값을 바꿔야 할 때, 물리적으로 분리된 빈이
// 아니면 토스 쪽까지 같이 영향받는다. 지금 이 파일을 만드는 건 "값을 다르게 하기 위해서"가
// 아니라 "둘이 서로 영향 안 주게 끊어놓기 위해서"
@Configuration
public class DifyConfig {

    // 연결 자체가 안 되는 상황(Dify 서버 다운, 네트워크 문제)은 응답을 기다릴 이유가 없어
    // 짧게 잡는다 - 이건 사용자 규모와 무관하게 항상 맞는 값이라 8초로 고정.
    private static final int DIFY_CONNECT_TIMEOUT_MILLISECONDS = 8_000;

    // 연결은 됐는데 응답이 안 오는 경우 - 120초를 유지한다.
    private static final int DIFY_READ_TIMEOUT_MILLISECONDS = 120_000;


    @Bean
    public RestTemplate difyRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(DIFY_CONNECT_TIMEOUT_MILLISECONDS);
        requestFactory.setReadTimeout(DIFY_READ_TIMEOUT_MILLISECONDS);
        return new RestTemplate(requestFactory);
    }

    // Dify 호출(챗봇 + 리포트 분석 둘 다)을 전담하는 전용 스레드풀.
    // 왜 필요한지: 컨트롤러가 이 요청을 처리하는 동안 Tomcat 워커 스레드가 Dify 응답을
    // 기다리며 블로킹되면, 그 스레드는 그동안 충전/로그인 등 완전히 무관한 다른 요청도
    // 못 받게 된다. 이 풀로 "Dify를 기다리는 역할"만 따로 떼어내서, Tomcat 스레드는
    // Dify 응답을 기다리지 않고 바로 반납되게 만든다 (ChatController/MoneyReportController가
    // 이 빈을 받아서 DeferredResult와 함께 씀).
    @Bean
    public ThreadPoolTaskExecutor difyTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);    // 평소 유지하는 스레드 수
        executor.setMaxPoolSize(4);     // 몰릴 때 최대로 늘어나는 스레드 수
        executor.setQueueCapacity(8);   // core가 다 찼을 때 추가로 대기시킬 수 있는 요청 수
        executor.setThreadNamePrefix("dify-");
        // 큐까지 꽉 찼을 때 호출한 스레드(Tomcat 워커)가 직접 떠맡는 CallerRunsPolicy는
        // 절대 쓰면 안 된다 - 그러면 우리가 없애려던 "Tomcat 스레드가 Dify를 기다리며
        // 블로킹"이 그대로 재현된다. AbortPolicy는 큐까지 꽉 차면 그냥 예외를 던져서
        // "지금 혼잡하니 나중에" 응답을 즉시 준다.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();  // 이 호출이 있어야 내부 ThreadPoolExecutor가 실제로 만들어짐
        return executor;
    }
}
