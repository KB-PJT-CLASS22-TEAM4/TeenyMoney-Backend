package com.teenyfin.teenymoney.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 서블릿(자식) 컨텍스트. 웹 계층만 여기 산다.
 *
 * 표준 Spring MVC 배치대로 @Controller와 @ControllerAdvice만 스캔한다. 서비스·매퍼·스토어는
 * RootConfig(부모)가 갖고, 자식은 부모 빈을 조회할 수 있으므로 컨트롤러 주입은 그대로 된다.
 * 반대는 안 된다 - 부모는 자식을 못 본다. 이 단방향이 계층 역전을 구조로 막아준다.
 *
 * useDefaultFilters = false가 핵심이다. 이게 없으면 기본 필터(@Component 전체)가 살아 있어
 * 서비스가 부모·자식에 두 벌 생긴다. 그러면 컨트롤러는 트랜잭션 프록시가 안 걸린 자식 사본을
 * 주입받아, 트랜잭션이 조용히 없는 상태가 된다.
 *
 * @EnableMethodSecurity가 여기 있는 이유: 이 애노테이션은 '자기가 속한 컨텍스트의 빈'에만
 * 프록시를 건다. @PreAuthorize는 지금 컨트롤러(자식)에만 붙어 있으므로 실제로 일하는 것은
 * 이쪽이다. 여기서 빼면 컨트롤러의 권한 검사가 예외도 로그도 없이 건너뛰어진다.
 * (RootConfig에도 같은 스위치가 있다. 서비스에 @PreAuthorize를 붙이는 날을 위한 대비다.)
 *
 * @EnableTransactionManagement와 @EnableScheduling은 RootConfig로 갔다. 대상 빈이 거기 있다.
 *
 * @Configuration이 필요한 이유: 이게 없어도 @ComponentScan 덕에 lite 모드로 처리돼 동작은 한다.
 * 다만 lite 모드는 CGLIB 프록시가 없어서, @Bean 메서드가 다른 @Bean 메서드를 호출하면
 * 싱글턴이 아니라 새 인스턴스가 조용히 만들어진다.
 */
@Configuration
@EnableWebMvc
@EnableMethodSecurity   // 없으면 컨트롤러의 @PreAuthorize가 조용히 무시된다
@ComponentScan(
        basePackages = {"com.teenyfin.teenymoney.domain", "com.teenyfin.teenymoney.global"},
        useDefaultFilters = false,
        includeFilters = {
                @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = Controller.class),
                @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = ControllerAdvice.class)
        })
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

    /**
     * API 서버라 src/main/webapp이 없다. 정적 리소스는 springfox jar 안의 Swagger UI가 전부다.
     * /swagger-resources, /v2/api-docs는 springfox 컨트롤러가 처리하므로 여기 둘 대상이 아니다.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/swagger-ui.html")
                .addResourceLocations("classpath:/META-INF/resources/");

        // Swagger UI가 로드하는 js/css
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
    }
}
