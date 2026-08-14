package com.teenyfin.teenymoney.domain.report.mapper;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MyBatis 매퍼 XML이 실제로 읽히는지 확인한다. DB에 붙지 않는다.
 *
 * MoneyReportMapperTest 는 DB 환경변수가 없으면 통째로 스킵되므로, 로컬 DB가 없는 사람과
 * CI 에서는 XML 이 한 번도 파싱되지 않는다. 오타 하나로 배포가 부팅에 실패하는 걸
 * 여기서 잡는다.
 *
 * MyBatis 는 SqlSessionFactory 를 만드는 시점에 매퍼 XML 을 전부 파싱한다. 커넥션은
 * 첫 쿼리에서야 필요하므로 연결되지 않는 DataSource 를 넘겨도 파싱 검증은 그대로 된다.
 * 이 한 번으로 XML 문법, resultType 클래스 존재 여부, statement id 중복,
 * 그리고 인터페이스 메서드와 XML id 의 불일치까지 걸린다.
 */
class MoneyReportMapperXmlTest {

    private static final String MAPPER_XML_PATTERN =
            "classpath*:com/teenyfin/teenymoney/**/mapper/*Mapper.xml";

    private SqlSessionFactory buildFactory() throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        PathMatchingResourcePatternResolver resolver =
                new PathMatchingResourcePatternResolver();

        factoryBean.setConfigLocation(resolver.getResource("classpath:/mybatis-config.xml"));
        factoryBean.setMapperLocations(resolver.getResources(MAPPER_XML_PATTERN));

        // 파싱만 하므로 연결되지 않는다. 드라이버 인스턴스만 있으면 된다.
        factoryBean.setDataSource(new SimpleDriverDataSource(
                new com.mysql.cj.jdbc.Driver(), "jdbc:mysql://localhost/none", "none", "none"));

        SqlSessionFactory factory = factoryBean.getObject();
        assertThat(factory).isNotNull();
        return factory;
    }

    @Test
    @DisplayName("매퍼 XML 전체가 파싱되고 MoneyReportMapper 의 모든 메서드에 대응 쿼리가 있다")
    void everyMapperMethodHasAStatement() throws Exception {
        var configuration = buildFactory().getConfiguration();

        for (Method method : MoneyReportMapper.class.getDeclaredMethods()) {
            String statementId = MoneyReportMapper.class.getName() + "." + method.getName();

            assertThat(configuration.hasStatement(statementId))
                    .as("%s 에 대응하는 쿼리가 MoneyReportMapper.xml 에 없습니다", method.getName())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("mapUnderscoreToCamelCase 가 켜져 있다")
    void underscoreMappingIsOn() throws Exception {
        // 이게 꺼지면 category_id -> categoryId 매핑이 조용히 null 이 된다.
        // VO 에 resultMap 을 두지 않은 전제 조건이다.
        assertThat(buildFactory().getConfiguration().isMapUnderscoreToCamelCase()).isTrue();
    }
}
