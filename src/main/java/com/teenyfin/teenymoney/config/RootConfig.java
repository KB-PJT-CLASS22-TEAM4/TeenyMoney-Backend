package com.teenyfin.teenymoney.config;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.PropertySource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.bind.annotation.ControllerAdvice;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.ZoneId;

/**
 * 루트(부모) 컨텍스트. WebConfig가 직접 등록하므로 컴포넌트 스캔 대상이 아니다.
 * config 패키지를 global 밖에 둔 이유가 이것이다 - 스캔 경계를 패키지 구조로 표현한다.
 *
 * 표준 Spring MVC 2-컨텍스트 배치를 따른다. Service·Repository·DB·트랜잭션이 여기 있고,
 * 웹 계층(@Controller, @ControllerAdvice)만 ServletConfig가 가져간다.
 *
 * 스캔에서 웹 계층을 빼는 기준을 패키지가 아니라 애노테이션으로 잡은 이유는
 * domain/<도메인>/{controller,service,mapper} 구조라 패키지 하나로 계층을 자를 수 없어서다.
 *
 * @EnableTransactionManagement / @EnableScheduling이 여기 있는 이유:
 * 둘 다 '자기가 속한 컨텍스트의 빈'에만 프록시를 걸거나 스캔한다. @Transactional을 붙인 서비스와
 * @Scheduled를 붙인 스케줄러가 이제 이 루트 컨텍스트에 있으므로 여기 둬야 한다.
 * 자식에만 두면 예외도 로그도 없이 통째로 건너뛰어진다.
 *
 * @EnableMethodSecurity는 현재 대상이 없다. @PreAuthorize는 컨트롤러(자식)에만 붙어 있고
 * ServletConfig가 그쪽을 담당한다. 그럼에도 여기 켜 두는 이유는, 서비스에 @PreAuthorize를
 * 붙이는 순간 이게 없으면 권한 검사가 '조용히' 사라지기 때문이다. 켜 두는 비용은 0이다
 * (대상 빈이 없으면 프록시도 안 만든다). 못 알아채는 쪽의 비용이 훨씬 크다.
 */
@Configuration
@EnableTransactionManagement   // 없으면 @Transactional이 조용히 무시된다
@EnableScheduling              // 없으면 @Scheduled가 조용히 실행되지 않는다
@EnableMethodSecurity          // 서비스에 @PreAuthorize를 붙일 때를 대비한 안전장치 (위 주석 참고)
@ComponentScan(
        basePackages = {"com.teenyfin.teenymoney.domain", "com.teenyfin.teenymoney.global"},
        excludeFilters = {
                // @RestController는 @Controller를 메타 애노테이션으로 갖고 있어 함께 걸린다.
                @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = Controller.class),
                // @RestControllerAdvice는 @Controller가 아니라 @Component 계열이라 따로 빼야 한다.
                @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = ControllerAdvice.class)
        })
@PropertySource({"classpath:/application.properties"})
// @Mapper 가 붙은 인터페이스만 MyBatis 매퍼로 등록한다.
// annotationClass 없이 패키지만 지정하면 그 안의 '모든' 인터페이스를 매퍼로 만들려 해서,
// 나중에 일반 인터페이스(정책 평가기 등)를 만들면 기동이 깨진다.
@MapperScan(basePackages = "com.teenyfin.teenymoney", annotationClass = Mapper.class)
public class RootConfig {
    private static final String MAPPER_XML_PATTERN =
            "classpath*:com/teenyfin/teenymoney/**/mapper/*Mapper.xml";

    @Value("${jdbc.driver}")
    private String driver;
    @Value("${jdbc.url}")
    private String url;
    @Value("${jdbc.username}")
    private String username;
    @Value("${jdbc.password}")
    private String password;

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }

    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName(driver);
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);

        // DB에 못 붙어도 애플리케이션은 뜬다.
        // 기본값(1)이면 부팅 시점에 연결을 시도하고 실패 시 컨텍스트 로딩이 통째로 깨져서,
        // 배포 문제인지 DB 문제인지 구분이 안 된다. 연결 실패는 /api/v1/health/db 로 확인한다.
        config.setInitializationFailTimeout(-1);

        HikariDataSource dataSource = new HikariDataSource(config);
        return dataSource;
    }

    @Autowired
    ApplicationContext applicationContext;

    @Bean
    public SqlSessionFactory sqlSessionFactory() throws Exception {
        SqlSessionFactoryBean sqlSessionFactory = new SqlSessionFactoryBean();
        sqlSessionFactory.setConfigLocation(
                applicationContext.getResource("classpath:/mybatis-config.xml"));
        sqlSessionFactory.setMapperLocations(
                applicationContext.getResources(MAPPER_XML_PATTERN));
        sqlSessionFactory.setDataSource(dataSource());
        return (SqlSessionFactory) sqlSessionFactory.getObject();
    }

    @Bean
    public DataSourceTransactionManager transactionManager(){
        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource());
        return manager;
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }

}
