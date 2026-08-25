package com.teenyfin.teenymoney.config;

import com.teenyfin.teenymoney.domain.charge.toss.TossPaymentsClient;
import com.teenyfin.teenymoney.domain.chatbot.dify.DifyClient;
import com.teenyfin.teenymoney.domain.report.dify.ReportAnalysisDifyClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * DifyConfig가 RestTemplateConfig(토스 전용) 옆에 두 번째 RestTemplate 빈(difyRestTemplate)을
 * 추가하면서, 같은 타입의 빈이 두 개인 상황이 처음으로 생겼다.
 *
 * TossPaymentsClient/DifyClient/ReportAnalysisDifyClient는 전부 @Qualifier로 자기가 원하는
 * 빈을 명시하고 있지만, 그게 실제로 컨텍스트 기동 시점에 NoUniqueBeanDefinitionException 없이
 * 맞물려 동작하는지 확인하는 테스트가 이전까지 하나도 없었다 (ChargeServiceTest 등은 이
 * 세 클래스를 전부 Mockito mock으로 대체해서 만들기 때문에, 실제 DI가 성공하는지는 검증하지 않는다).
 *
 * DB/Redis가 필요 없는 좁은 컨텍스트(RestTemplateConfig + DifyConfig + 세 클라이언트)만 띄워서,
 * 이 세 빈이 각자 올바른 RestTemplate을 받는지를 실제 스프링 DI로 증명한다.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        RestTemplateConfig.class,
        DifyConfig.class,
        TossPaymentsClient.class,
        DifyClient.class,
        ReportAnalysisDifyClient.class
})
@TestPropertySource(properties = {
        // toss.secret-key/dify.api-key/dify.report-api-key는 application.properties에만 기본값이
        // 있고 @Value 표현식 자체엔 없다 - 이 테스트는 RootConfig(@PropertySource로 그 파일을 읽는 곳)를
        // 안 띄우므로, 여기서 직접 값을 안 주면 컨텍스트 기동 자체가 실패한다.
        "toss.secret-key=test-toss-secret-key",
        "dify.api-key=test-dify-api-key",
        "dify.report-api-key=test-dify-report-api-key"
})
class DifyRestTemplateWiringTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private TossPaymentsClient tossPaymentsClient;

    @Autowired
    private DifyClient difyClient;

    @Autowired
    private ReportAnalysisDifyClient reportAnalysisDifyClient;

    @Test
    @DisplayName("restTemplate(토스)과 difyRestTemplate(Dify)이 서로 다른 빈 인스턴스로 등록된다")
    void twoDistinctRestTemplateBeansAreRegistered() {
        RestTemplate tossRestTemplate = applicationContext.getBean("restTemplate", RestTemplate.class);
        RestTemplate difyRestTemplate = applicationContext.getBean("difyRestTemplate", RestTemplate.class);

        assertNotNull(tossRestTemplate);
        assertNotNull(difyRestTemplate);
        // 같은 인스턴스면 타임아웃 분리(1단계 작업 전체의 목적)가 무의미해진다.
        assertNotSame(tossRestTemplate, difyRestTemplate,
                "토스와 Dify가 같은 RestTemplate 인스턴스를 쓰면 타임아웃이 다시 하나로 합쳐진 것과 같다.");
    }

    @Test
    @DisplayName("TossPaymentsClient/DifyClient/ReportAnalysisDifyClient가 모호성 에러 없이 각자 주입된다")
    void eachClientResolvesWithoutAmbiguity() {
        // @Autowired 필드 주입 자체가 여기까지 성공했다는 것 = 컨텍스트 기동 시점에
        // NoUniqueBeanDefinitionException이 안 났다는 뜻. 클래스 필드 접근만으로도
        // 사실상 검증은 끝나지만, 명시적으로 한 번 더 확인한다.
        assertNotNull(tossPaymentsClient);
        assertNotNull(difyClient);
        assertNotNull(reportAnalysisDifyClient);
    }

    @Test
    @DisplayName("difyTaskExecutor가 core=2/max=4/queue=8로 등록된다")
    void difyTaskExecutorHasExpectedPoolSizing() {
        ThreadPoolTaskExecutor executor =
                applicationContext.getBean("difyTaskExecutor", ThreadPoolTaskExecutor.class);

        assertNotNull(executor);
        assertEquals(2, executor.getCorePoolSize());
        assertEquals(4, executor.getMaxPoolSize());
        // ThreadPoolTaskExecutor 자체엔 큐 용량을 직접 읽는 getter가 없어서,
        // 내부적으로 감싸고 있는 실제 ThreadPoolExecutor의 큐를 통해 확인한다.
        assertEquals(8, executor.getThreadPoolExecutor().getQueue().remainingCapacity());
    }
}
