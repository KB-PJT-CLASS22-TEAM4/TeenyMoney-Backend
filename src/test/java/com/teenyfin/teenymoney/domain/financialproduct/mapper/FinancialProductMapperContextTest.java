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
}
