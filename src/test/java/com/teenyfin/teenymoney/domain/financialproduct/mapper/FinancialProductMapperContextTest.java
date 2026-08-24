package com.teenyfin.teenymoney.domain.financialproduct.mapper;

import com.teenyfin.teenymoney.config.LazyBeanInitializer;
import com.teenyfin.teenymoney.config.RootConfig;
import com.teenyfin.teenymoney.domain.financialproduct.vo.DepositProductVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.SavingProductVO;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("금융상품 혜택은 실시간 점수가 아닌 월간 적용 등급으로 조회한다")
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
    @DisplayName("동일 상품의 여러 계약을 enrollmentId로 구분하여 조회한다")
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
    @DisplayName("적금 계약 조회에 자유·정액 유형과 이자 계산 방식을 포함한다")
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
    @DisplayName("금감원 상품 동기화 시 기존 상품 설명도 갱신한다")
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

    @Test
    @DisplayName("부모 생성 상품은 생성 부모와 대상 자녀에게만 노출한다")
    void visibleProductQueryRestrictsParentProductsToOwnerOrTargetChild() {
        String namespace = FinancialProductMapper.class.getName();
        String sql = sqlSessionFactory.getConfiguration()
                .getMappedStatement(namespace + ".selectVisibleDepositProducts")
                .getBoundSql(Map.of("memberId", 2L))
                .getSql()
                .replaceAll("\\s+", " ")
                .trim();

        assertTrue(sql.contains("product.product_source IN ('TEENY', 'FINLIFE')"));
        assertTrue(sql.contains("product.target_child_id = ?"));
        assertTrue(sql.contains("product.created_by_parent_id = ?"));
    }

    @Test
    @DisplayName("예적금 상품 조회와 생성 SQL에 요구등급을 포함한다")
    void depositAndSavingQueriesIncludeRequiredGrade() {
        String namespace = FinancialProductMapper.class.getName();
        String depositSelect = sqlSessionFactory.getConfiguration()
                .getMappedStatement(namespace + ".selectVisibleDepositProducts")
                .getBoundSql(Map.of("memberId", 2L)).getSql()
                .replaceAll("\\s+", " ").trim();
        String savingSelect = sqlSessionFactory.getConfiguration()
                .getMappedStatement(namespace + ".selectVisibleSavingProducts")
                .getBoundSql(Map.of("memberId", 2L)).getSql()
                .replaceAll("\\s+", " ").trim();
        String depositInsert = sqlSessionFactory.getConfiguration()
                .getMappedStatement(namespace + ".insertCustomDepositProduct")
                .getBoundSql(new DepositProductVO()).getSql()
                .replaceAll("\\s+", " ").trim();
        String savingInsert = sqlSessionFactory.getConfiguration()
                .getMappedStatement(namespace + ".insertCustomSavingProduct")
                .getBoundSql(new SavingProductVO()).getSql()
                .replaceAll("\\s+", " ").trim();

        assertTrue(depositSelect.contains(
                "product.required_grade_id = required_grade.grade_id"));
        assertTrue(savingSelect.contains(
                "product.required_grade_id = required_grade.grade_id"));
        assertTrue(depositInsert.contains("required_grade_id"));
        assertTrue(savingInsert.contains("required_grade_id"));
    }
}
