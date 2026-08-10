package com.teenyfin.teenymoney.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 컨텍스트의 모든 빈을 지연 생성으로 바꾼다. 테스트에서만 쓴다.
 *
 * RootConfig는 표준 Spring MVC 배치대로 Service·Repository까지 스캔한다. 그래서
 * 매퍼 등록만 확인하려는 테스트가 RootConfig를 올리면 서비스 계층 전체가 함께 생성되고,
 * MemberService -> S3Storage -> S3Client 처럼 이 테스트와 무관한 외부 의존까지 요구한다.
 *
 * 여기서 전부 지연으로 돌려두면 테스트가 실제로 @Autowired 한 빈과 그 의존만 만들어진다.
 * 운영은 그대로 즉시 생성이라 기동 시점에 배선 오류를 잡는 성질(fail-fast)을 잃지 않는다.
 *
 * 주의: 이걸 붙인 테스트는 '컨텍스트가 통째로 뜬다'는 것을 더 이상 보장하지 않는다.
 * 전체 기동 검증이 목적이라면 붙이지 말 것.
 */
public class LazyBeanInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        context.addBeanFactoryPostProcessor(beanFactory -> {
            for (String name : beanFactory.getBeanDefinitionNames()) {
                beanFactory.getBeanDefinition(name).setLazyInit(true);
            }
        });
    }
}
