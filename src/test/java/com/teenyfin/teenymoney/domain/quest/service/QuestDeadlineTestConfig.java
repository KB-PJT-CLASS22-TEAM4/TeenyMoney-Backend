package com.teenyfin.teenymoney.domain.quest.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/**
 * 마감 배치 DB 통합 테스트용 최소 컨텍스트.
 *
 * RootConfig 를 통째로 올리지 않는 이유가 있다. RootConfig 의 컴포넌트 스캔은 domain 전체를
 * 잡아서 결제·인증·S3·Redis 빈까지 만들려 하고, 그 의존성들은 형제 설정 클래스(RestTemplateConfig,
 * SecurityConfig 등)에 흩어져 있다. 결국 이 테스트와 무관한 설정을 전부 나열해야 하고,
 * 다른 도메인이 빈을 하나 추가할 때마다 여기가 깨진다.
 *
 * 이 테스트에 필요한 것은 DataSource, MyBatis 매퍼, 트랜잭션 매니저뿐이다.
 * 검증 대상이 SQL 이므로 애플리케이션 전체를 띄울 이유가 없다.
 */
@Configuration
@EnableTransactionManagement
@PropertySource("classpath:/application.properties")
@MapperScan(
        basePackages = {
                "com.teenyfin.teenymoney.domain.quest.mapper",
                "com.teenyfin.teenymoney.domain.teenyscore.mapper"
        },
        annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class QuestDeadlineTestConfig {

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

    @Autowired
    private ApplicationContext applicationContext;

    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName(driver);
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        // SKIP LOCKED 테스트가 연결 두 개를 동시에 연다. 기본값(10)으로 충분하지만 명시해 둔다.
        config.setMaximumPoolSize(5);
        return new HikariDataSource(config);
    }

    @Bean
    public SqlSessionFactory sqlSessionFactory() throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setConfigLocation(
                applicationContext.getResource("classpath:/mybatis-config.xml"));
        factory.setMapperLocations(applicationContext.getResources(MAPPER_XML_PATTERN));
        factory.setDataSource(dataSource());
        return factory.getObject();
    }

    @Bean
    public java.time.Clock clock() {
        return java.time.Clock.fixed(
                java.time.Instant.parse("2000-01-02T01:00:00Z"),
                java.time.ZoneId.of("Asia/Seoul"));
    }

    @Bean
    public PlatformTransactionManager transactionManager() {
        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource());
        // 마감 배치의 건별 격리가 SAVEPOINT 에 의존한다. RootConfig 와 같은 전제로 맞춘다.
        manager.setNestedTransactionAllowed(true);
        return manager;
    }
}
