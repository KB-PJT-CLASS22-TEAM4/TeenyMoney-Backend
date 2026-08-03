package com.teenyfin.teenymoney.domain.member.mapper;

import com.teenyfin.teenymoney.config.RootConfig;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    private String normalizedInsertSql(MemberVO member) {
        BoundSql boundSql = sqlSessionFactory.getConfiguration()
                .getMappedStatement(NAMESPACE + ".insert")
                .getBoundSql(member);
        return boundSql.getSql().replaceAll("\\s+", " ").trim();
    }
}
