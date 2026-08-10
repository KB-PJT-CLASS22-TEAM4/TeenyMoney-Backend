package com.teenyfin.teenymoney.domain.charge.toss;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 토스페이먼츠 자동결제(빌링) API와 직접 통신하는 담당 클래스.
// FinlifeClient(금감원 API 연동)랑 똑같은 역할 - "외부 API 하나를 감싸는 전용 컴포넌트".
// ChargeMethodService는 토스 API의 URL이나 인증 헤더 형식 같은 걸 전혀 몰라도 되고, 그냥 이 클래스의 메서드만 호출하면 됨.

@Slf4j          // Lombok이 log.info(...)/log.warn(...) 등을 쓸 수 있게 로거 필드를 자동 생성
@Component      // 이 클래스를 스프링 빈으로 등록 - @Autowired 없이도 생성자 주입 가능

public class TossPaymentsClient {

    private static final int TIMEOUT_MILLISECONDS = 10_000;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper; // 에러 응답 JSON 문자열을 TossErrorResponseDTO로 파싱할 때 씀
    private final String secretKey;
    private final String baseUrl;

    // @Autowired: 스프링이 이 빈을 만들 때 이 생성자를 쓰라고 명시.
    // @Value("${toss.secret-key}") : application.properties에 적을 toss.secret-key 값을
    // 스프링이 이 생성자를 호출할 때 자동으로 넣어줌. FinlifeClient의 apiKey 주입 방식과 동일한 패턴.

}
