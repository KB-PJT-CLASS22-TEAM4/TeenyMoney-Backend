package com.teenyfin.teenymoney.config;


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
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/**
 * 루트(부모) 컨텍스트. WebConfig가 직접 등록하므로 컴포넌트 스캔 대상이 아니다.
 * config 패키지를 global 밖에 둔 이유가 이것이다 - 스캔 경계를 패키지 구조로 표현한다.
 */
@Configuration
@EnableTransactionManagement   // 없으면 @Transactional이 조용히 무시된다
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

}
