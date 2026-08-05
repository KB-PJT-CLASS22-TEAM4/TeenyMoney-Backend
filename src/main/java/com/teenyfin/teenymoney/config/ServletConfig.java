package com.teenyfin.teenymoney.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 서블릿(자식) 컨텍스트. Controller와 그 아래 계층이 여기 산다.
 *
 * 스캔 대상은 domain, global 두 곳이다. config 패키지는 여기 없으므로
 * RootConfig가 자식 컨텍스트에 중복 등록되지 않는다.
 * (섞여 있으면 DataSource·TransactionManager가 두 벌 생겨 트랜잭션 경계가 깨진다)
 *
 * @EnableMethodSecurity가 SecurityConfig(루트)가 아니라 여기 있는 이유:
 * 이 애노테이션은 '자기가 속한 컨텍스트의 빈'에만 프록시를 건다. @PreAuthorize를 붙일
 * 컨트롤러·서비스가 이 자식 컨텍스트에 있으므로 여기 둬야 한다. 루트에 두면 애노테이션은
 * 붙어 있는데 권한 검사가 조용히 통째로 건너뛰어진다(예외도 로그도 없다).
 */
@EnableWebMvc
@EnableMethodSecurity
@EnableTransactionManagement   // 없으면 @Transactional이 조용히 무시된다
@ComponentScan(basePackages = {"com.teenyfin.teenymoney.domain",
                                "com.teenyfin.teenymoney.global"})
public class ServletConfig implements WebMvcConfigurer {

    /**
     * 멀티파트 파싱기. 반드시 이 자식 컨텍스트에, 반드시 "multipartResolver"라는
     * 이름으로 둔다. DispatcherServlet이 자기 컨텍스트에서 이 이름으로 찾기 때문이다.
     *
     * 루트에 두거나 이름이 다르면 예외도 로그도 없이 멀티파트 파싱이 일어나지 않고,
     * @RequestParam MultipartFile 이 비어서 들어온다.
     *
     * 실제 용량 상한은 WebConfig의 MultipartConfigElement가 건다.
     */
    @Bean
    public MultipartResolver multipartResolver() {
        return new StandardServletMultipartResolver();
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(
                "/api/v1",
                HandlerTypePredicate.forAnnotation(RestController.class)
        );
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry
                .addResourceHandler("/resources/**") // url이 /resources/로 시작하는 모든 경로
                .addResourceLocations("/resources/"); // webapp/resources/경로로 매핑

        // Swagger UI 리소스를 위한 핸들러 설정
        registry.addResourceHandler("/swagger-ui.html")
                .addResourceLocations("classpath:/META-INF/resources/");

        // Swagger WebJar 리소스 설정
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");

        // Swagger 리소스 설정
        registry.addResourceHandler("/swagger-resources/**")
                .addResourceLocations("classpath:/META-INF/resources/");

        registry.addResourceHandler("/v2/api-docs")
                .addResourceLocations("classpath:/META-INF/resources/");

    }
}
