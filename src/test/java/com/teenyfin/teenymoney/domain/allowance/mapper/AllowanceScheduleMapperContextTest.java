package com.teenyfin.teenymoney.domain.allowance.mapper;

import com.teenyfin.teenymoney.config.LazyBeanInitializer;
import com.teenyfin.teenymoney.config.RootConfig;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 실 DB 없이 XML이 정상 로드되는지만 확인 (FinancialProductMapperContextTest와 동일 원리 -
// 가짜 JDBC URL + LazyBeanInitializer로 연결 시도 없이 MyBatis 설정만 파싱).
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
class AllowanceScheduleMapperContextTest {

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    // XML에 정의한 8개 SQL(insert, selectByParentId 등)이 전부 MyBatis에 정상 등록됐는지
    // (Mapper 인터페이스 메서드명과 XML id가 안 맞으면 여기서 걸림)
    @Test
    void allMapperMethodsAreRegisteredWithMybatis() {
        String prefix = AllowanceScheduleMapper.class.getName() + ".";
        String[] methodNames = {
                "insert", "selectByParentId", "selectById", "update",
                "updateActiveAndNextPaymentDate", "updateNextPaymentDate",
                "deleteById", "selectDueScheduleIds"
        };

        for (String methodName : methodNames) {
            assertNotNull(
                    sqlSessionFactory.getConfiguration().getMappedStatement(prefix + methodName),
                    methodName + " 매핑 statement가 등록되어야 한다");
        }
    }

    // selectDueScheduleIds SQL 문자열 안에 is_active, next_payment_date 컬럼이 실제로 들어있는지
    // (SQL을 통째로 실행하지 않고 문자열만 확인 - 실 DB 없이도 배치 조회 기준이 맞는지 검증)
    @Test
    void selectDueScheduleIdsFiltersByActiveAndDate() {
        String sql = sqlSessionFactory.getConfiguration()
                .getMappedStatement(AllowanceScheduleMapper.class.getName() + ".selectDueScheduleIds")
                .getBoundSql(null).getSql();

        assertTrue(sql.contains("is_active"));
        assertTrue(sql.contains("next_payment_date"));
    }
}
