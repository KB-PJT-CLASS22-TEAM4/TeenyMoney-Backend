package com.teenyfin.teenymoney.config;
// 외부 API(토스페이먼츠 등)를 호출할 때 여러 클라이언트가 공통으로 쓸 RestTemplate을
// 여기서 딱 한 번만 만들어서 스프링 빈으로 등록한다.
// @Configuration: 이 클래스 안의 @Bean 메서드들이 리턴하는 객체를 스프링이 관리하는
// 빈으로 등록하겠다는 표시 (RootConfig, S3Config랑 같은 역할).

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    private static final int TIMEOUT_MILLISECONDS = 10_000;

    // @Bean: 이 메서드가 리턴하는 RestTemplate 객체 하나를 스프링 컨테이너에 등록.
    // 이후 어디서든 생성자에 `RestTemplate restTemplate` 파라미터를 두면 스프링이
    // 바로 이 객체를 자동으로 찾아서 넣어줌 (TossPaymentsClient가 그렇게 받을 예정).
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT_MILLISECONDS);
        requestFactory.setReadTimeout(TIMEOUT_MILLISECONDS);
        return new RestTemplate(requestFactory);
    }
}
