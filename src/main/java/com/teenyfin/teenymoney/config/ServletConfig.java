package com.teenyfin.teenymoney.config;

import org.springframework.context.annotation.ComponentScan;
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
 */
@EnableWebMvc
@ComponentScan(basePackages = {"com.teenyfin.teenymoney.domain",
                                "com.teenyfin.teenymoney.global"})
public class ServletConfig implements WebMvcConfigurer {
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(
                "/api/v1",
                HandlerTypePredicate.forAnnotation(RestController.class)
        );
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/api-docs/**")
                .addResourceLocations("classpath:/openapi/");

        registry.addResourceHandler("/swagger-ui/**")
                .addResourceLocations(
                        "classpath:/swagger-ui/",
                        "classpath:/META-INF/resources/webjars/swagger-ui/5.31.0/"
          );
    }
}
