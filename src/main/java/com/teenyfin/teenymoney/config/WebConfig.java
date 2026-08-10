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

    // "/"는 default 매핑이라 다른 서블릿이 가져가지 않는 모든 경로가 여기로 온다.
    // Swagger 경로를 덧붙이지 말 것. 서블릿 스펙의 매핑 패턴은 "/path/*", "*.ext", "/", 정확일치뿐이라
    // "/webjars/**" 같은 걸 적으면 리터럴 문자열 정확일치로 등록되어 아무것도 매칭하지 않는다.
    @Override
    protected String[] getServletMappings() {
        return new String[] {"/"};
    }

    @Override
    protected Filter[] getServletFilters() {
        // forceEncoding(true)는 응답에도 UTF-8을 강제한다. 이게 없으면 컨테이너가
        // 지정한 기본 인코딩이 그대로 나가 한글 응답이 깨진다.
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
