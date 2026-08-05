package com.teenyfin.teenymoney.config;

import com.teenyfin.teenymoney.global.storage.ImageFile;
import org.springframework.web.filter.DelegatingFilterProxy;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.servlet.support.AbstractAnnotationConfigDispatcherServletInitializer;
import javax.servlet.Filter;
import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletRegistration;

public class WebConfig extends AbstractAnnotationConfigDispatcherServletInitializer {
    @Override
    protected Class<?>[] getRootConfigClasses() {
        return new Class[] {
                RootConfig.class,
                RedisConfig.class,
                SecurityConfig.class,
                S3Config.class
        };
    }

    @Override
    protected Class<?>[] getServletConfigClasses() {
        return new Class[] {ServletConfig.class, SwaggerConfig.class};
    }

    @Override
    protected String[] getServletMappings() {
        return new String[] {
                "/",
                "/swagger-ui.html",
                "/swagger-resources/**",
                "/v2/api-docs",
                "/webjars/**"
        };
    }

    // POSTbody문자인코딩필터설정-UTF-8설정
    @Override
    protected Filter[] getServletFilters() {
        CharacterEncodingFilter characterEncodingFilter= new CharacterEncodingFilter();
        characterEncodingFilter.setEncoding("UTF-8");
        characterEncodingFilter.setForceEncoding(true);

        DelegatingFilterProxy securityFilter =
                new DelegatingFilterProxy("springSecurityFilterChain");

        return new Filter[] {characterEncodingFilter, securityFilter};
    }

    // 요청 전체 상한은 파일 상한보다 커야 한다. 멀티파트 요청에는 경계 문자열과
    // 파트 헤더가 함께 실리므로, 둘을 같은 값으로 두면 정확히 5MB인 파일이
    // 오버헤드 때문에 컨테이너에서 잘린다.
    private static final long MAX_REQUEST_BYTES = ImageFile.MAX_BYTES + 1024 * 1024;

    @Override
    protected void customizeRegistration(ServletRegistration.Dynamic registration) {
        registration.setInitParameter("throwExceptionIfNoHandlerFound", "true");

        // 컨테이너 레벨 상한. 이게 없으면 Tomcat이 수백 MB짜리를 임시 디스크에 전부
        // 받아놓은 뒤에야 ImageFile 검증이 거절한다.
        // 파일 상한은 ImageFile과 같은 값을 써서 두 곳이 어긋나지 않게 한다.
        // fileSizeThreshold를 0으로 두면 메모리에 쌓지 않고 바로 임시 파일로 쓴다.
        registration.setMultipartConfig(new MultipartConfigElement(
                null, ImageFile.MAX_BYTES, MAX_REQUEST_BYTES, 0));
    }
}
