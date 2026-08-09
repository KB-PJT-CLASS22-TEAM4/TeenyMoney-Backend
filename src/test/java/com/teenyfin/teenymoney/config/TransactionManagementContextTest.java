package com.teenyfin.teenymoney.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.autoproxy.InfrastructureAdvisorAutoProxyCreator;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Transactional이 실제로 걸리는지 검증한다.
 *
 * 이 프로젝트는 스프링 컨텍스트가 두 개다.
 *   부모(Root)   : RootConfig, RedisConfig, SecurityConfig, S3Config
 *                  — DataSource, 트랜잭션 매니저, 매퍼, '서비스'
 *   자식(Servlet): ServletConfig — @Controller·@ControllerAdvice만 스캔
 *
 * @EnableTransactionManagement는 트랜잭션 매니저를 만드는 게 아니라
 * '자기 컨텍스트의 빈에 트랜잭션 프록시를 붙이는' 애노테이션이다. 빈 후처리기는
 * 자기 BeanFactory의 빈에만 적용되므로, 켠 곳과 @Transactional 빈이 있는 곳이 어긋나면
 * @Transactional은 예외도 로그도 없이 통째로 무시된다.
 *
 * 아래 두 테스트가 그 메커니즘을 실제 스프링으로 증명하고(방향만 바꿔 읽으면 된다),
 * 세 번째 테스트가 이 저장소의 배치를 고정한다.
 */
class TransactionManagementContextTest {

    /** @Transactional이 붙은 서비스 자리. CGLIB이 상속할 수 있게 final이 아니어야 한다. */
    static class SampleService {
        @Transactional
        public void doWork() {
            // 트랜잭션이 걸렸는지만 보므로 본문은 비운다.
        }
    }

    /** 부모 컨텍스트: 트랜잭션 매니저를 갖고 @EnableTransactionManagement를 켠다 (= 현재 RootConfig). */
    @Configuration
    @EnableTransactionManagement
    static class ParentWithTransactionManagement {
    }

    /**
     * 자식 컨텍스트: 서비스만 있고 @EnableTransactionManagement가 없다 (= 현재 ServletConfig).
     *
     * 실제 ServletConfig는 @EnableMethodSecurity 때문에 자동 프록시 생성기를 이미 갖고 있다.
     * "생성기가 없어서 프록시가 안 생긴 것 아니냐"는 반론을 막기 위해 여기서도 같은 생성기를
     * 직접 등록한다. 생성기가 있어도 부모의 트랜잭션 어드바이저는 적용되지 않는다는 것이 요점이다.
     */
    @Configuration
    static class ChildWithoutTransactionManagement {
        @Bean
        static InfrastructureAdvisorAutoProxyCreator autoProxyCreator() {
            return new InfrastructureAdvisorAutoProxyCreator();
        }

        @Bean
        SampleService sampleService() {
            return new SampleService();
        }
    }

    /** 자식 컨텍스트에 @EnableTransactionManagement를 켠 경우 (= 제안하는 수정안). */
    @Configuration
    @EnableTransactionManagement
    static class ChildWithTransactionManagement {
        @Bean
        SampleService sampleService() {
            return new SampleService();
        }
    }

    private PlatformTransactionManager stubTransactionManager() {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        return transactionManager;
    }

    private AnnotationConfigApplicationContext parentContext(PlatformTransactionManager transactionManager) {
        AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext();
        parent.registerBean(PlatformTransactionManager.class, () -> transactionManager);
        parent.register(ParentWithTransactionManagement.class);
        parent.refresh();
        return parent;
    }

    private AnnotationConfigApplicationContext childContext(AnnotationConfigApplicationContext parent,
                                                           Class<?> childConfig) {
        AnnotationConfigApplicationContext child = new AnnotationConfigApplicationContext();
        child.setParent(parent);
        child.register(childConfig);
        child.refresh();
        return child;
    }

    @Test
    @DisplayName("부모에만 @EnableTransactionManagement가 있으면 자식의 @Transactional은 무시된다")
    void transactionalIsIgnoredWhenEnabledOnlyInParentContext() {
        PlatformTransactionManager transactionManager = stubTransactionManager();

        try (AnnotationConfigApplicationContext parent = parentContext(transactionManager);
             AnnotationConfigApplicationContext child =
                     childContext(parent, ChildWithoutTransactionManagement.class)) {

            child.getBean(SampleService.class).doWork();

            // 트랜잭션이 시작조차 되지 않았다. 롤백할 대상이 없으므로 예외가 나도 되돌아가지 않는다.
            verify(transactionManager, never()).getTransaction(any());
        }
    }

    @Test
    @DisplayName("자식에 @EnableTransactionManagement를 켜면 트랜잭션이 걸린다 (부모의 매니저를 찾아 쓴다)")
    void transactionalIsAppliedWhenEnabledInChildContext() {
        PlatformTransactionManager transactionManager = stubTransactionManager();

        try (AnnotationConfigApplicationContext parent = parentContext(transactionManager);
             AnnotationConfigApplicationContext child =
                     childContext(parent, ChildWithTransactionManagement.class)) {

            child.getBean(SampleService.class).doWork();

            // 트랜잭션 매니저는 부모에만 있다. 자식이 부모 빈을 찾아 쓸 수 있다는 것까지 함께 증명한다.
            // 그래서 수정은 '애노테이션 한 줄 이동'이면 되고 매니저를 옮길 필요가 없다.
            verify(transactionManager).getTransaction(any());
            verify(transactionManager).commit(any());
        }
    }

    /**
     * 이 저장소의 배치를 고정한다.
     *
     * RootConfig가 @Transactional이 붙은 서비스를 루트 컨텍스트로 스캔하고,
     * 같은 클래스에서 스위치를 켠다.
     *
     * 스위치를 ServletConfig(자식)로 되돌리면 이 테스트가 실패한다.
     */
    @Test
    @DisplayName("@Transactional 서비스를 스캔하는 루트 컨텍스트에서 트랜잭션 관리가 켜져 있어야 한다")
    void rootContextMustEnableTransactionManagement() {
        assertNotNull(
                AnnotationUtils.findAnnotation(RootConfig.class, EnableTransactionManagement.class),
                "@Transactional이 붙은 서비스는 ServiceConfig가 스캔하는 루트 컨텍스트에 있다. "
                        + "@EnableTransactionManagement가 ServletConfig(자식)에만 있으면 트랜잭션 프록시가 "
                        + "생성되지 않아 모든 @Transactional이 조용히 무시된다. "
                        + "RootConfig에 @EnableTransactionManagement를 둘 것.");
    }
}
