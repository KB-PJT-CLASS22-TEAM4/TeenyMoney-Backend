package com.teenyfin.teenymoney.domain.financialproduct.mapper;

import com.teenyfin.teenymoney.config.LazyBeanInitializer;
import com.teenyfin.teenymoney.config.RootConfig;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(
        classes = RootConfig.class,
        initializers = LazyBeanInitializer.class)
@TestPropertySource(properties = {
        "jdbc.driver=com.mysql.cj.jdbc.Driver",
        "jdbc.url=jdbc:mysql://127.0.0.1:1/teenymoney",
        "jdbc.username=test",
        "jdbc.password=test"
})
class FinancialProductMapperContextTest {

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Test
    void benefitQueryUsesAppliedGradeInsteadOfRealtimeScoreRange() {
        String statement = FinancialProductMapper.class.getName()
                + ".selectBenefitByChildId";
        String sql = sqlSessionFactory.getConfiguration()
                .getMappedStatement(statement)
                .getBoundSql(2L)
                .getSql()
                .replaceAll("\\s+", " ")
                .trim();

        assertTrue(sql.contains(
                "member.applied_grade_id = grade.grade_id"));
        assertFalse(sql.contains(
                "member.teeny_score BETWEEN grade.min_score"));
    }

    @Test
    void enrollmentQueriesKeepContractsSeparateAndUseEnrollmentIdForDetail() {
        String namespace = FinancialProductMapper.class.getName();
        String listSql = sqlSessionFactory.getConfiguration()
                .getMappedStatement(namespace
                        + ".selectDepositEnrollmentsByChildId")
                .getBoundSql(Map.of("childId", 2L))
                .getSql()
                .replaceAll("\\s+", " ")
                .trim();
        String detailSql = sqlSessionFactory.getConfiguration()
                .getMappedStatement(namespace
                        + ".selectDepositEnrollmentByChildIdAndId")
                .getBoundSql(Map.of(
                        "childId", 2L,
                        "enrollmentId", 11L))
                .getSql()
                .replaceAll("\\s+", " ")
                .trim();

        assertFalse(listSql.contains("SELECT DISTINCT"));
        assertTrue(listSql.contains("enrollment.id AS enrollment_id"));
        assertTrue(detailSql.contains("enrollment.id = ?"));
        assertFalse(detailSql.contains("product.id = ?"));
    }

    @Test
    void savingEnrollmentQueriesIncludeProductClassification() {
        String namespace = FinancialProductMapper.class.getName();
        String listSql = sqlSessionFactory.getConfiguration()
                .getMappedStatement(namespace
                        + ".selectSavingEnrollmentsByChildId")
                .getBoundSql(Map.of("childId", 2L))
                .getSql()
                .replaceAll("\\s+", " ")
                .trim();
        String detailSql = sqlSessionFactory.getConfiguration()
                .getMappedStatement(namespace
                        + ".selectSavingEnrollmentByChildIdAndId")
                .getBoundSql(Map.of(
                        "childId", 2L,
                        "enrollmentId", 31L))
                .getSql()
                .replaceAll("\\s+", " ")
                .trim();

        assertTrue(listSql.contains("product.savings_type"));
        assertTrue(detailSql.contains("product.savings_type"));
        assertTrue(listSql.contains("product.interest_calculation_type"));
        assertTrue(detailSql.contains("product.interest_calculation_type"));
    }

    @Test
    void productSyncUpdatesExistingDescriptions() {
        String namespace = FinancialProductMapper.class.getName();
        String depositSql = sqlSessionFactory.getConfiguration()
                .getMappedStatement(namespace + ".upsertDepositProduct")
                .getBoundSql(new Object())
                .getSql()
                .replaceAll("\\s+", " ")
                .trim();
        String savingSql = sqlSessionFactory.getConfiguration()
                .getMappedStatement(namespace + ".upsertSavingProduct")
                .getBoundSql(new Object())
                .getSql()
                .replaceAll("\\s+", " ")
                .trim();

        assertTrue(depositSql.contains(
                "description = VALUES(description)"));
        assertTrue(savingSql.contains(
                "description = VALUES(description)"));
    }
}
