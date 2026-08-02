package com.teenyfin.teenymoney.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 인프라 설정.
 *
 * Refresh Token, 가족 연동 코드처럼 문자열 기반 데이터를 안전하게 다룰 수 있도록
 * StringRedisTemplate을 기본 템플릿으로 제공한다.
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory(
            @Value("${redis.host}") String host,
            @Value("${redis.port}") int port,
            @Value("${redis.password:}") String password) {
        RedisStandaloneConfiguration configuration =
                new RedisStandaloneConfiguration(host, port);

        // requirepass가 걸린 서버(EC2)는 이게 없으면 모든 명령이 NOAUTH로 실패한다.
        // Lettuce는 지연 연결이라 앱은 정상 기동하고, 첫 Redis 명령(로그인 시
        // Refresh Token 저장)에서야 터진다. 로컬 Redis처럼 비밀번호가 없으면
        // 빈 문자열이 들어오고, RedisPassword.of가 그것을 '비밀번호 없음'으로 처리한다.
        configuration.setPassword(password);

        return new LettuceConnectionFactory(configuration);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(
            RedisConnectionFactory redisConnectionFactory) {
        return new StringRedisTemplate(redisConnectionFactory);
    }
}
