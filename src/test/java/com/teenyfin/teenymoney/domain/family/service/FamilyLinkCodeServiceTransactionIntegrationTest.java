package com.teenyfin.teenymoney.domain.family.service;

import com.teenyfin.teenymoney.config.RootConfig;
import com.teenyfin.teenymoney.domain.categoryPolicy.mapper.CategoryPolicyMapper;
import com.teenyfin.teenymoney.domain.family.store.FamilyLinkCodeStore;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class FamilyLinkCodeServiceTransactionIntegrationTest {

    private AnnotationConfigApplicationContext parentContext;
    private AnnotationConfigApplicationContext childContext;

    private FamilyLinkCodeService service;
    private FamilyLinkCodeStore store;
    private MemberMapper memberMapper;
    private JdbcTemplate jdbcTemplate;

    private Long parentId;
    private Long childId;

    @BeforeEach
    void setUp() {
        parentContext = new AnnotationConfigApplicationContext();
        parentContext.register(
                RootConfig.class,
                ParentTestConfig.class
        );
        parentContext.refresh();

        childContext = new AnnotationConfigApplicationContext();
        childContext.setParent(parentContext);
        childContext.register(ChildTestConfig.class);
        childContext.refresh();

        service = childContext.getBean(FamilyLinkCodeService.class);
        store = parentContext.getBean(FamilyLinkCodeStore.class);
        memberMapper = parentContext.getBean(MemberMapper.class);

        DataSource dataSource = parentContext.getBean(DataSource.class);
        jdbcTemplate = new JdbcTemplate(dataSource);

        parentId = insertMember("PARENT");
        childId = insertMember("CHILD");
    }

    @AfterEach
    void tearDown() {
        if (jdbcTemplate != null) {
            if (childId != null) {
                jdbcTemplate.update(
                        "DELETE FROM T_MCC_POLICY_M WHERE child_id = ?",
                        childId
                );
                jdbcTemplate.update(
                        "DELETE FROM T_MBR_CONN_R WHERE child_id = ?",
                        childId
                );
            }

            if (childId != null) {
                jdbcTemplate.update(
                        "DELETE FROM T_MBR_INFO_M WHERE id = ?",
                        childId
                );
            }

            if (parentId != null) {
                jdbcTemplate.update(
                        "DELETE FROM T_MBR_INFO_M WHERE id = ?",
                        parentId
                );
            }
        }

        if (childContext != null) {
            childContext.close();
        }

        if (parentContext != null) {
            parentContext.close();
        }
    }

    @Test
    void policyInsertFailureRollsBackFamilyConnection() {
        when(store.incrementConsumeAttempts(eq(childId), any()))
                .thenReturn(1L);
        when(store.consumeCode("048291"))
                .thenReturn(parentId);

        /*
         * 자녀의 카테고리 정책 한 건을 미리 생성한다.
         * 서비스가 전체 기본 정책을 INSERT할 때
         * UQ_MCC_POLICY_M_CHILD_CATEGORY 제약 위반이 발생한다.
         */
        jdbcTemplate.update(
                """
                INSERT INTO T_MCC_POLICY_M (
                    parent_id,
                    child_id,
                    merchant_category_id,
                    policy
                )
                SELECT ?, ?, id, default_policy
                FROM T_MCC_CTGR_C
                ORDER BY id
                LIMIT 1
                """,
                parentId,
                childId
        );

        assertThrows(
                DuplicateKeyException.class,
                () -> service.linkChild(childId, "048291")
        );

        Integer connectionCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM T_MBR_CONN_R
                WHERE parent_id = ?
                  AND child_id = ?
                """,
                Integer.class,
                parentId,
                childId
        );

        assertEquals(
                0,
                connectionCount,
                "정책 생성 실패 시 가족 관계도 롤백되어야 한다"
        );
    }

    private Long insertMember(String role) {
        String unique = UUID.randomUUID()
                .toString()
                .replace("-", "");

        MemberVO member = new MemberVO();
        member.setRole(role);
        member.setName("transaction-test");
        member.setBirthDate(
                "PARENT".equals(role)
                        ? LocalDate.of(1980, 1, 1)
                        : LocalDate.of(2010, 1, 1)
        );
        member.setPhoneNumber("010" + unique.substring(0, 8));
        member.setEmail(unique + "@test.local");
        member.setPassword("test-password");

        memberMapper.insert(member);
        return member.getId();
    }

    @Configuration
    static class ParentTestConfig {

        @Bean
        FamilyLinkCodeStore familyLinkCodeStore() {
            return mock(FamilyLinkCodeStore.class);
        }
    }

    @Configuration
    @EnableTransactionManagement
    static class ChildTestConfig {

        @Bean
        FamilyLinkCodeService familyLinkCodeService(
                FamilyLinkCodeStore store,
                CategoryPolicyMapper categoryPolicyMapper,
                MemberMapper memberMapper,
                Clock clock
        ) {
            return new FamilyLinkCodeService(
                    store,
                    categoryPolicyMapper,
                    memberMapper,
                    clock
            );
        }
    }
}