package com.teenyfin.teenymoney.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = {
        RedisConfig.class,
        SecurityConfig.class
})
@TestPropertySource(properties = {
        "redis.host=localhost",
        "redis.port=6379",
        // SecurityConfig가 @Value로 읽는 값들. 없으면 컨텍스트 자체가 뜨지 않는다.
        "jwt.secret=b5ZbpsxM9qd3XTR09Hm/tTbpmTybNbUp/Qc51yyPmk4=",
        "jwt.access-expiration=1800000",
        "jwt.refresh-expiration=1209600000"
})
class InfrastructureConfigTest {

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void redisInfrastructureBeansAreRegistered() {
        assertNotNull(redisConnectionFactory);
        assertNotNull(stringRedisTemplate);
    }

    @Test
    void securityFilterChainIsRegistered() {
        assertNotNull(applicationContext.getBean("springSecurityFilterChain"));
    }

    /**
     * 보안 빈이 '루트 컨텍스트'에 등록됐는지 확인한다.
     * 자식 컨텍스트(ServletConfig)에 만들어지면 필터체인이 못 보고
     * 인증이 조용히 통째로 동작하지 않는다.
     */
    @Test
    void securityBeansAreRegistered() {
        assertNotNull(applicationContext.getBean(
                com.teenyfin.teenymoney.global.security.jwt.JwtProvider.class));
        assertNotNull(applicationContext.getBean(
                com.teenyfin.teenymoney.global.security.jwt.JwtAuthenticationFilter.class));
        assertNotNull(applicationContext.getBean(
                org.springframework.security.crypto.password.PasswordEncoder.class));
    }
}
