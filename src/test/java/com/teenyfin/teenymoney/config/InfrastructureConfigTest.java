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
        "redis.port=6379"
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
}
