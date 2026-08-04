package com.teenyfin.teenymoney.domain.auth.service;

import com.teenyfin.teenymoney.config.RootConfig;
import com.teenyfin.teenymoney.domain.auth.dto.request.SignupRequestDTO;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.global.auth.RefreshTokenStore;
import com.teenyfin.teenymoney.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

//환경변수들이 존재할때만 실행된다는 Annotation
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
// 트랜잭션 통합 테스트 클래스
public class AuthServiceTransactionIntegrationTest {

    // 부모 Spring Context, RootConfig, DataSource, TransactionManager, MemberMapper
    private AnnotationConfigApplicationContext parentContext;
    // AuthSerivce
    private AnnotationConfigApplicationContext childContext;

    // 실제 테스트할 AuthService 빈
    private AuthService authService;
    // 회원가입 인증번호 소비 단계에서 강제로 예외를 발생시킬 Mock
    private PhoneVerificationService phoneVerificationService;
    // 실제 Bcrypt 연산을 피하고 고정 문자열 반환할 Mock data
    private PasswordEncoder passwordEncoder;
    // 회원 데이터가 실제 DB에 남았는지 직접 조회
    private JdbcTemplate jdbcTemplate;

    // 테스트마다 겹치지 않는 이메일 보관, 삭제
    private String email;

    @BeforeEach
    void Setup() {

        // 비어있는 Annotation 기반 Spring Context 생성
        parentContext = new AnnotationConfigApplicationContext();
        // 부모 Context에 실제 RootConfig와 테스트 설정 등록
        parentContext.register(RootConfig.class, ParentTestConfig.class);
        parentContext.refresh();

        // AuthService가 들어갈 별도의 자식 Context 생성
        childContext = new AnnotationConfigApplicationContext();
        childContext.setParent(parentContext);
        // 자식에 ChildTestConfig 등록
        childContext.register(ChildTestConfig.class);
        childContext.refresh();

        // 자식 Context에서 테스트 대상 AuthService 가져옴
        authService = childContext.getBean(AuthService.class);
        // 부모 Context에서 Mock Phone..Service가져옴.
        phoneVerificationService =
                parentContext.getBean(PhoneVerificationService.class);

        passwordEncoder =
                parentContext.getBean(PasswordEncoder.class);
        // 부모 Context에 등록된 실제 DataSource를 가져옵니다.
        DataSource dataSource =
                parentContext.getBean(DataSource.class);

        // 해당 DataSource로 SQL을 직접 실행할 JdbcTemplate을 만듭니다.
        jdbcTemplate = new JdbcTemplate(dataSource);

        email = "transaction-" + UUID.randomUUID() + "@test.local";

        when(passwordEncoder.encode(anyString()))
                .thenReturn("$2a$10$test-only-password-hash");
    }

    @AfterEach
    void tearDown() {
        /*
         * 테스트가 실패해서 회원 데이터가 남더라도 정리한다.
         * 테스트 클래스에는 @Transactional을 붙이면 안 된다.
         */
        if (jdbcTemplate != null && email != null) {
            jdbcTemplate.update(
                    "DELETE FROM T_MBR_INFO_M WHERE email = ?",
                    email
            );
        }

        if (childContext != null) {
            childContext.close();
        }

        if (parentContext != null) {
            parentContext.close();
        }
    }

    @Test
    void authServiceShouldBeWrappedByTransactionProxy() {
        assertTrue(
                AopUtils.isAopProxy(authService),
                "AuthService에 트랜잭션 프록시가 적용되지 않았습니다."
        );
    }

    @Test
    void signupShouldRollbackMemberInsertWhenLaterStepFails() {
        SignupRequestDTO request = signupRequest();

        /*
         * AuthService.signup() 실행 순서:
         *
         * 1. phoneVerificationService.verify()
         * 2. memberMapper.insert()
         * 3. phoneVerificationService.consume()
         *
         * INSERT 이후 실행되는 consume()에서 RuntimeException을 발생시킨다.
         */
        doThrow(new IllegalStateException("강제 실패"))
                .when(phoneVerificationService)
                .consume(request.getPhoneNumber());

        assertThrows(
                IllegalStateException.class,
                () -> authService.signup(request)
        );

        Integer storedCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM T_MBR_INFO_M
                WHERE email = ?
                """,
                Integer.class,
                email
        );

        assertEquals(
                0,
                storedCount,
                "예외가 발생했지만 회원 INSERT가 롤백되지 않았습니다."
        );
    }

    private SignupRequestDTO signupRequest() {
        String unique =
                UUID.randomUUID().toString().replace("-", "");

        int phoneSuffix =
                Math.floorMod(unique.hashCode(), 100_000_000);

        SignupRequestDTO request = new SignupRequestDTO();
        request.setName("트랜잭션 테스트");
        request.setBirthDate(LocalDate.of(2010, 1, 2));
        request.setPhoneNumber(
                String.format("010%08d", phoneSuffix)
        );
        request.setVerificationCode("123456");
        request.setEmail(email);
        request.setPassword("password123");

        return request;
    }

    /*
     * 부모 컨텍스트에서 AuthService가 요구하는 의존성을 제공한다.
     * MemberMapper, DataSource, TransactionManager, Clock은 RootConfig가 제공한다.
     */
    @Configuration
    static class ParentTestConfig {

        @Bean
        PhoneVerificationService phoneVerificationService() {
            return mock(PhoneVerificationService.class);
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return mock(PasswordEncoder.class);
        }

        @Bean
        JwtProvider jwtProvider() {
            return mock(JwtProvider.class);
        }

        @Bean
        RefreshTokenStore refreshTokenStore() {
            return mock(RefreshTokenStore.class);
        }
    }

    /*
     * 실제 프로젝트에서 AuthService가 Servlet 자식 컨텍스트에 생성되는
     * 상황만 최소한으로 재현한다.
     */
    @Configuration
    @EnableTransactionManagement
    static class ChildTestConfig {

        @Bean
        AuthService authService(
                MemberMapper memberMapper,
                PasswordEncoder passwordEncoder,
                PhoneVerificationService phoneVerificationService,
                JwtProvider jwtProvider,
                RefreshTokenStore refreshTokenStore,
                Clock clock) {

            return new AuthService(
                    memberMapper,
                    passwordEncoder,
                    phoneVerificationService,
                    jwtProvider,
                    refreshTokenStore,
                    clock
            );
        }
    }


}
