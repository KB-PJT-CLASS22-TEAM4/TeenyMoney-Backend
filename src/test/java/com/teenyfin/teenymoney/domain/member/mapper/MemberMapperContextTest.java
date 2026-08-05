package com.teenyfin.teenymoney.domain.member.mapper;

import com.teenyfin.teenymoney.config.RootConfig;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RootConfig.class)
@TestPropertySource(properties = {
        "jdbc.driver=com.mysql.cj.jdbc.Driver",
        "jdbc.url=jdbc:mysql://127.0.0.1:1/teenymoney",
        "jdbc.username=test",
        "jdbc.password=test"
})
class MemberMapperContextTest {

    private static final String NAMESPACE = MemberMapper.class.getName();

    @Autowired
    private MemberMapper memberMapper;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Test
    void memberMapperAndAllStatementsAreRegistered() {
        assertNotNull(memberMapper);

        for (String statement : Arrays.asList(
                "selectByEmail", "selectById", "existsByEmail",
                "existsByPhoneNumber", "insert")) {
            assertTrue(sqlSessionFactory.getConfiguration()
                    .hasStatement(NAMESPACE + "." + statement));
        }
    }

    @Test
    void insertUsesGeneratedMemberId() {
        MappedStatement insert = sqlSessionFactory.getConfiguration()
                .getMappedStatement(NAMESPACE + ".insert");

        assertArrayEquals(new String[]{"id"}, insert.getKeyProperties());
    }

    @Test
    void parentGetsNullTeenyScoreAndChildUsesDatabaseDefault() {
        MemberVO parent = new MemberVO();
        parent.setRole("PARENT");
        MemberVO child = new MemberVO();
        child.setRole("CHILD");

        String parentSql = normalizedInsertSql(parent);
        String childSql = normalizedInsertSql(child);

        assertTrue(parentSql.contains("teeny_score"));
        assertTrue(parentSql.contains("NULL"));
        assertFalse(childSql.contains("teeny_score"));
    }

    @Test
    @DisplayName("resultMap이 profile_image_key 컬럼을 profileImageKey 필드로 매핑한다")
    void resultMapBindsProfileImageKeyColumn() {
        // 이 매핑이 틀려도 MyBatis는 예외를 던지지 않는다. 해당 필드가 조용히 null이 되고,
        // 프로필 이미지가 안 보인다는 제보를 받고 나서야 알게 된다.
        // 컬럼명을 profile_image_url -> profile_image_key로 바꿨으므로(#55) 여기서 고정한다.
        ResultMap resultMap = sqlSessionFactory.getConfiguration()
                .getResultMap(NAMESPACE + ".memberResultMap");

        Map<String, String> propertyByColumn = resultMap.getResultMappings().stream()
                .collect(Collectors.toMap(ResultMapping::getColumn, ResultMapping::getProperty));

        System.out.printf("    입력: memberResultMap의 컬럼 -> 필드 매핑%n"
                        + "    기대: profile_image_key -> profileImageKey (profile_image_url 없음)%n"
                        + "    실제: profile_image_key -> %s, profile_image_url -> %s%n%n",
                propertyByColumn.get("profile_image_key"),
                propertyByColumn.get("profile_image_url"));

        assertEquals("profileImageKey", propertyByColumn.get("profile_image_key"),
                "profile_image_key가 profileImageKey로 매핑되지 않는다");
        assertNull(propertyByColumn.get("profile_image_url"),
                "옛 컬럼명 profile_image_url이 아직 남아 있다");
    }

    @Test
    @DisplayName("조회 쿼리가 profile_image_key 컬럼을 SELECT 한다")
    void selectQueryReadsProfileImageKeyColumn() {
        // resultMap이 맞아도 SELECT 목록에 컬럼이 없으면 값이 오지 않는다.
        // memberColumns 조각과 resultMap은 따로 관리되므로 둘 다 확인해야 한다.
        String sql = sqlSessionFactory.getConfiguration()
                .getMappedStatement(NAMESPACE + ".selectById")
                .getBoundSql(1L)
                .getSql()
                .replaceAll("\\s+", " ");

        System.out.printf("    입력: selectById의 SELECT 목록%n"
                        + "    기대: profile_image_key 포함, profile_image_url 미포함%n"
                        + "    실제: profile_image_key=%s, profile_image_url=%s%n%n",
                sql.contains("profile_image_key") ? "포함" : "없음",
                sql.contains("profile_image_url") ? "포함" : "없음");

        assertTrue(sql.contains("profile_image_key"), sql);
        assertFalse(sql.contains("profile_image_url"), sql);
    }

    private String normalizedInsertSql(MemberVO member) {
        BoundSql boundSql = sqlSessionFactory.getConfiguration()
                .getMappedStatement(NAMESPACE + ".insert")
                .getBoundSql(member);
        return boundSql.getSql().replaceAll("\\s+", " ").trim();
    }
}
