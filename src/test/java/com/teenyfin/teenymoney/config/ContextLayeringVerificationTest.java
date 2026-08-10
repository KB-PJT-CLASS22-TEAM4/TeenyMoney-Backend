package com.teenyfin.teenymoney.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2-컨텍스트 계층 배치를 고정한다.
 *
 * 이 구조에서 잘못 배치했을 때 나는 고장은 전부 조용하다. 트랜잭션 프록시가 안 걸려도,
 * @PreAuthorize가 건너뛰어져도, @RestControllerAdvice를 DispatcherServlet이 못 찾아도
 * 예외도 로그도 없다. 나머지 단위 테스트는 손으로 만든 테스트 설정을 쓰기 때문에
 * 배치가 깨져 있어도 전부 통과한다. 그래서 여기서는 운영 설정 클래스 자체를 대상으로 삼는다.
 *
 * 검증은 세 조각이고, TransactionManagementContextTest와 이어 붙이면 결론이 나온다.
 *   1. 파티션 : 운영 @ComponentScan 필터가 서비스를 루트로, 웹 계층을 자식으로 정확히 가른다
 *   2. 동거   : 스캔과 스위치가 같은 RootConfig에 있고, 그게 루트 컨텍스트에 올라간다
 *   3. 자식   : 컨트롤러의 @PreAuthorize를 위해 ServletConfig에도 스위치가 남아 있다
 *   + 메커니즘: 스위치와 빈이 같은 컨텍스트면 프록시가 걸린다 (TransactionManagementContextTest)
 *
 * ── 왜 '실제로 프록시가 붙었는지'를 여기서 확인하지 않는가 ──────────────────────
 *
 * 시도했고, 신뢰할 수 없어서 뺐다. RootConfig를 띄워 실제 서비스의 어드바이저를 보는
 * 테스트를 만들었더니, RootConfig에서 @EnableTransactionManagement를 지워도 통과했다.
 * 운영 스캔 범위(domain·global) 안에 테스트 클래스가 같은 패키지로 들어와 있고,
 * 그중 둘이 중첩 @Configuration에 @EnableTransactionManagement를 갖고 있어서
 * 스캔이 스위치를 도로 켜버리기 때문이다.
 *
 *   domain/auth/service/AuthServiceTransactionIntegrationTest
 *   domain/family/service/FamilyLinkCodeServiceTransactionIntegrationTest
 *
 * 운영 WAR에는 테스트 클래스가 없으므로 운영 결함은 아니다. 다만 '테스트 클래스패스에서
 * 운영 스캔을 돌리는' 검증은 이 저장소에서 원리적으로 못 믿는다는 뜻이다.
 * 진짜 종단 확인은 애플리케이션을 띄워서 요청을 보내는 것뿐이다.
 */
class ContextLayeringVerificationTest {

    private static final String SERVICE = "com.teenyfin.teenymoney.domain.wallet.service.WalletService";
    private static final String CONTROLLER = "com.teenyfin.teenymoney.domain.wallet.controller.WalletController";
    private static final String ADVICE = "com.teenyfin.teenymoney.global.exception.GlobalExceptionAdvice";
    private static final String SCHEDULER =
            "com.teenyfin.teenymoney.domain.financialproduct.scheduler.FinancialProductSyncScheduler";

    /**
     * 설정 클래스에 달린 '진짜' @ComponentScan을 읽어 같은 필터로 스캔한다.
     * 필터를 복사해 적는 게 아니라 애노테이션 값을 그대로 쓰므로,
     * 운영 설정이 바뀌면 이 테스트도 따라 바뀐다.
     */
    @SuppressWarnings("unchecked")
    private static Set<String> scanCandidates(Class<?> configClass) {
        ComponentScan scan = AnnotationUtils.findAnnotation(configClass, ComponentScan.class);
        assertNotNull(scan, configClass.getSimpleName() + "에 @ComponentScan이 있어야 한다.");

        ClassPathScanningCandidateComponentProvider provider =
                new ClassPathScanningCandidateComponentProvider(scan.useDefaultFilters());
        for (ComponentScan.Filter filter : scan.includeFilters()) {
            for (Class<?> annotation : filter.classes()) {
                provider.addIncludeFilter(
                        new AnnotationTypeFilter((Class<? extends Annotation>) annotation));
            }
        }
        for (ComponentScan.Filter filter : scan.excludeFilters()) {
            for (Class<?> annotation : filter.classes()) {
                provider.addExcludeFilter(
                        new AnnotationTypeFilter((Class<? extends Annotation>) annotation));
            }
        }

        Set<String> candidates = new TreeSet<>();
        for (String basePackage : scan.basePackages()) {
            provider.findCandidateComponents(basePackage).stream()
                    .map(AnnotatedBeanDefinition.class::cast)
                    .forEach(definition -> candidates.add(definition.getBeanClassName()));
        }
        return candidates;
    }

    // ---------- 1. 파티션 ----------

    @Test
    @DisplayName("서비스·스케줄러는 루트 스캔에만, 컨트롤러·어드바이스는 자식 스캔에만 잡힌다")
    void scanFiltersPartitionTheLayers() {
        Set<String> root = scanCandidates(RootConfig.class);
        Set<String> servlet = scanCandidates(ServletConfig.class);

        assertTrue(root.contains(SERVICE), "서비스가 루트 스캔에 없다: " + SERVICE);
        assertTrue(root.contains(SCHEDULER), "스케줄러가 루트 스캔에 없다: " + SCHEDULER);
        assertTrue(servlet.contains(CONTROLLER), "컨트롤러가 자식 스캔에 없다: " + CONTROLLER);
        assertTrue(servlet.contains(ADVICE),
                "@RestControllerAdvice가 자식 스캔에 없다. DispatcherServlet은 자기 컨텍스트에서만 "
                        + "어드바이스를 찾으므로, 루트로 가면 예외 처리가 조용히 죽고 "
                        + "모든 에러가 컨테이너 기본 HTML로 나간다: " + ADVICE);
    }

    @Test
    @DisplayName("같은 클래스가 두 컨텍스트에 동시에 잡히지 않는다")
    void noBeanIsScannedIntoBothContexts() {
        Set<String> overlap = new HashSet<>(scanCandidates(RootConfig.class));
        overlap.retainAll(scanCandidates(ServletConfig.class));

        assertEquals(Set.of(), overlap,
                "부모와 자식에 같은 빈이 두 벌 생긴다. 그러면 컨트롤러는 트랜잭션 프록시가 걸리지 않은 "
                        + "자식 사본을 주입받아 롤백이 조용히 사라진다. "
                        + "ServletConfig의 useDefaultFilters = false가 풀렸는지 확인할 것.");
    }

    // ---------- 2. 동거: 스위치와 스캔이 같은 컨텍스트인가 ----------

    /** getRootConfigClasses()가 protected라 같은 패키지에서 노출만 시킨다. */
    static class WebConfigProbe extends WebConfig {
        Class<?>[] rootConfigClasses() {
            return getRootConfigClasses();
        }
    }

    @Test
    @DisplayName("스캔과 스위치가 같은 설정(RootConfig)에 있고, 그 설정이 루트 컨텍스트에 올라간다")
    void switchAndScanShareTheSameContext() {
        assertNotNull(AnnotationUtils.findAnnotation(RootConfig.class, ComponentScan.class),
                "서비스 계층 스캔이 RootConfig에 없다.");
        assertNotNull(
                AnnotationUtils.findAnnotation(RootConfig.class, EnableTransactionManagement.class),
                "트랜잭션 스위치가 RootConfig에 없다. 스캔과 스위치가 다른 컨텍스트로 갈리면 "
                        + "@Transactional이 예외도 로그도 없이 무시된다.");

        List<Class<?>> rootClasses = Arrays.asList(new WebConfigProbe().rootConfigClasses());
        assertTrue(rootClasses.contains(RootConfig.class),
                "RootConfig가 WebConfig의 루트 목록에 없다. 자식으로 내려가면 "
                        + "서블릿 필터(springSecurityFilterChain)가 빈을 못 본다.");
    }

    // ---------- 3. 자식: 컨트롤러의 @PreAuthorize ----------

    @Test
    @DisplayName("컨트롤러의 @PreAuthorize를 위해 ServletConfig에 @EnableMethodSecurity가 남아 있다")
    void servletContextKeepsMethodSecurityForControllers() {
        assertNotNull(
                AnnotationUtils.findAnnotation(ServletConfig.class, EnableMethodSecurity.class),
                "@PreAuthorize는 FamilyController 등 자식 컨텍스트의 컨트롤러에 붙어 있다. "
                        + "ServletConfig에서 @EnableMethodSecurity를 빼면 권한 검사가 "
                        + "예외도 로그도 없이 통째로 건너뛰어진다.");
    }
}
