package com.teenyfin.teenymoney.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

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
}
