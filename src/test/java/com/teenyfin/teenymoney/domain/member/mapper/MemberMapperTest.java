package com.teenyfin.teenymoney.domain.member.mapper;

import com.teenyfin.teenymoney.config.RootConfig;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RootConfig.class)
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@Transactional
class MemberMapperTest {

    @Autowired
    private MemberMapper memberMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void insertAssignsIdAndMemberCanBeSelectedByIdAndEmail() {
        MemberVO member = newMember("PARENT");

        assertEquals(1, memberMapper.insert(member));
        assertNotNull(member.getId());

        MemberVO byId = memberMapper.selectById(member.getId());
        MemberVO byEmail = memberMapper.selectByEmail(member.getEmail());

        assertMemberEquals(member, byId);
        assertMemberEquals(member, byEmail);
        assertEquals("ACTIVE", byId.getStatus());
    }

    @Test
    void existenceQueriesDistinguishStoredAndUnknownValues() {
        MemberVO member = newMember("CHILD");
        memberMapper.insert(member);

        assertTrue(memberMapper.existsByEmail(member.getEmail()));
        assertTrue(memberMapper.existsByPhoneNumber(member.getPhoneNumber()));
        assertFalse(memberMapper.existsByEmail("missing-" + member.getEmail()));
        assertFalse(memberMapper.existsByPhoneNumber("01000000000"));
    }

    @Test
    void insertAppliesRoleSpecificTeenyScorePolicy() {
        MemberVO parent = newMember("PARENT");
        MemberVO child = newMember("CHILD");

        memberMapper.insert(parent);
        memberMapper.insert(child);

        assertNull(teenyScore(parent.getId()));
        assertEquals(600, teenyScore(child.getId()));
    }

    private MemberVO newMember(String role) {
        String unique = UUID.randomUUID().toString().replace("-", "");
        int phoneSuffix = Math.floorMod(unique.hashCode(), 100_000_000);

        MemberVO member = new MemberVO();
        member.setRole(role);
        member.setName("Task2 테스트");
        member.setBirthDate(LocalDate.of(2010, 1, 2));
        member.setPhoneNumber(String.format("010%08d", phoneSuffix));
        member.setEmail("task2-" + unique + "@test.local");
        member.setPassword("$2a$10$test-only-hash");
        return member;
    }

    private Integer teenyScore(Long memberId) {
        return jdbcTemplate.queryForObject(
                "SELECT teeny_score FROM T_MBR_INFO_M WHERE id = ?",
                Integer.class,
                memberId);
    }

    private void assertMemberEquals(MemberVO expected, MemberVO actual) {
        assertNotNull(actual);
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getRole(), actual.getRole());
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getBirthDate(), actual.getBirthDate());
        assertEquals(expected.getPhoneNumber(), actual.getPhoneNumber());
        assertEquals(expected.getEmail(), actual.getEmail());
        assertEquals(expected.getPassword(), actual.getPassword());
    }
}
