package com.teenyfin.teenymoney.domain.auth.service;

import com.teenyfin.teenymoney.config.RootConfig;
import com.teenyfin.teenymoney.domain.auth.dto.request.SignupRequestDTO;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScoreGradeService;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.service.WalletService;
import com.teenyfin.teenymoney.global.auth.RefreshTokenStore;
import com.teenyfin.teenymoney.global.auth.LegalGuardianConsentStore;
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
import java.util.Map;
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
    // 지갑 생성 검증 테스트에서만 채워짐 - tearDown()에서 지갑을 먼저 지우는 데 씀
    private Long createdMemberId;

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
        // 지갑이 생성된 테스트라면, FK(ON DELETE RESTRICT) 때문에 회원보다 지갑을 먼저 지워야 한다.
        if (jdbcTemplate != null && createdMemberId != null) {
            jdbcTemplate.update(
                    "DELETE FROM T_WLT_BASE_M WHERE member_id = ?",
                    createdMemberId
            );
            jdbcTemplate.update(
                    "DELETE FROM T_MBR_AGRMT_H WHERE member_id = ?",
                    createdMemberId
            );
        }

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
         * 3. walletService.createWallet()
         * 4. phoneVerificationService.consume()
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

    @Test
    void signupShouldCreateMemberWalletWithZeroBalance() {
        SignupRequestDTO request = signupRequest();

        //when : 이번에 강제로 실패시키는 것 없이 회원가입을 끝까지 실행
        var response = authService.signup(request);
        createdMemberId = response.getMemberId();

        // then: 방금 만들어진 memberId로 T_WLT_BASE_M에 MEMBER 타입 지갑이
        // 실제로 생성됐는지 DB를 직접 조회해서 확인한다.
        // (queryForMap은 결과가 0건이면 그 자체로 예외를 던지므로,
        //  지갑이 아예 안 생겼을 때도 이 테스트가 실패로 알려준다.)
        Map<String, Object> wallet = jdbcTemplate.queryForMap(
                "SELECT balance, type FROM T_WLT_BASE_M WHERE member_id = ? AND type = 'MEMBER'",
                createdMemberId
        );
        assertEquals("MEMBER", wallet.get("type"));
        assertEquals(0L, ((Number) wallet.get("balance")).longValue());
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
        request.setPasswordConfirm("password123");
        request.setServiceTermsAgreed(true);
        request.setPrivacyAgreed(true);
        request.setServiceTermsVersion("1");
        request.setPrivacyTermsVersion("1");

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

        @Bean
        LegalGuardianConsentStore legalGuardianConsentStore() {
            return mock(LegalGuardianConsentStore.class);
        }

        @Bean
        WalletService walletService(WalletMapper walletMapper, Clock clock) {
            // 이 테스트는 지갑이 실제로 DB에 생기는지 확인해야 하니, 다른 협력자들처럼
            // mock으로 두지 않고 RootConfig가 제공하는 진짜 WalletMapper로 진짜 WalletService를 만든다.
            return new WalletService(walletMapper, clock);
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
                LegalGuardianConsentStore legalGuardianConsentStore,
                Clock clock,
                WalletService walletService,
                TeenyScoreGradeService teenyScoreGradeService
                ) {

            return new AuthService(
                    memberMapper,
                    passwordEncoder,
                    phoneVerificationService,
                    jwtProvider,
                    refreshTokenStore,
                    legalGuardianConsentStore,
                    clock,
                    walletService,
                    teenyScoreGradeService
            );
        }
    }


}
