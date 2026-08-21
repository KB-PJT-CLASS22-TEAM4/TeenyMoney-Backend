package com.teenyfin.teenymoney.domain.member.mapper;

import com.teenyfin.teenymoney.config.RootConfig;
import com.teenyfin.teenymoney.domain.member.vo.MemberChildVO;
import com.teenyfin.teenymoney.domain.member.vo.MemberParentVO;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.List;
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

    // RootConfig는 JdbcTemplate 빈을 등록하지 않는다. 프로덕션 코드가 쓰지 않기 때문이다.
    // 여기서만 필요하므로 DataSource로 직접 만든다. 같은 트랜잭션에 참여해야 하므로
    // 반드시 컨텍스트의 DataSource를 써야 한다(새로 만들면 롤백 대상에서 빠진다).
    private JdbcTemplate jdbcTemplate;

    @Autowired
    void setDataSource(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

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

    @Test
    @DisplayName("updateProfileImageKey 호출 -> 실제 컬럼이 갱신되고 조회로 왕복한다 (#55)")
    void updateProfileImageKeyWritesToRealColumn() {
        // 컬럼명을 profile_image_url -> profile_image_key로 바꿨다(V002).
        // 매퍼 XML과 실제 DB 컬럼이 어긋나면 여기서 SQL 오류나 null로 드러난다.
        // insert는 이 컬럼을 넣지 않으므로(V007, DEFAULT를 살리기 위해) update가 유일한 쓰기 경로다.
        MemberVO member = newMember("CHILD");
        memberMapper.insert(member);

        int updated = memberMapper.updateProfileImageKey(member.getId(), "profile/99/new.png");

        MemberVO loaded = memberMapper.selectById(member.getId());
        // 매퍼를 거치지 않고 컬럼을 직접 읽어 이름 자체를 못 박는다.
        String rawColumn = jdbcTemplate.queryForObject(
                "SELECT profile_image_key FROM T_MBR_INFO_M WHERE id = ?",
                String.class, member.getId());

        System.out.printf("    입력: updateProfileImageKey(%d, profile/99/new.png)%n"
                        + "    기대: 1행 갱신, 조회·직접읽기 모두 profile/99/new.png%n"
                        + "    실제: %d행 갱신, selectById=%s, 컬럼 직접읽기=%s%n%n",
                member.getId(), updated, loaded.getProfileImageKey(), rawColumn);

        assertEquals(1, updated);
        assertEquals("profile/99/new.png", loaded.getProfileImageKey());
        assertEquals("profile/99/new.png", rawColumn);
    }

    @Test
    @DisplayName("프로필 이미지 없이 가입 -> 기본 프로필 이미지 key가 들어간다 (V007)")
    void memberWithoutProfileImageGetsDefaultKey() {
        // insert가 profile_image_key 컬럼을 넣지 않아야 DB DEFAULT가 적용된다.
        // 매퍼가 #{profileImageKey}로 명시적 NULL을 보내면 NOT NULL 제약에 걸려 여기서 터진다.
        MemberVO member = newMember("CHILD");
        memberMapper.insert(member);

        MemberVO loaded = memberMapper.selectById(member.getId());

        System.out.printf("    입력: profileImageKey 없이 insert (대다수 회원)%n"
                        + "    기대: teenymoney_profile.png%n    실제: %s%n%n",
                loaded.getProfileImageKey());

        assertEquals("teenymoney_profile.png", loaded.getProfileImageKey());
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

    // --- selectChildrenByParentId -----------------------------------------------

    @Test
    void selectChildrenByParentIdJoinsMemberWalletAndDefaultsMissingWalletToZero() {
        MemberVO parent = newMember("PARENT");
        memberMapper.insert(parent);

        MemberVO withWallet = newMember("CHILD");
        memberMapper.insert(withWallet);
        MemberVO withoutWallet = newMember("CHILD");
        memberMapper.insert(withoutWallet);

        link(parent.getId(), withWallet.getId(), "ACTIVE");
        link(parent.getId(), withoutWallet.getId(), "ACTIVE");
        wallet(withWallet.getId(), 96500L, "MEMBER");
        // 예금 지갑은 잔액에 섞이면 안 된다. w.type = 'MEMBER' 조건이 없으면 행이 두 개로 늘어난다.
        wallet(withWallet.getId(), 50000L, "DEPOSIT");

        List<MemberChildVO> children =
                memberMapper.selectChildrenByParentId(parent.getId());

        assertEquals(2, children.size());

        MemberChildVO funded = byId(children, withWallet.getId());
        assertEquals(96500L, funded.getBalance());
        assertEquals(600, funded.getTeenyScore());   // CHILD는 DB 기본값 600
        assertEquals(withWallet.getEmail(), funded.getEmail());
        assertEquals(withWallet.getBirthDate(), funded.getBirthDate());

        // 지갑이 아직 없는 자녀도 목록에 남아야 한다. INNER JOIN이면 여기서 통째로 사라진다.
        assertEquals(0L, byId(children, withoutWallet.getId()).getBalance());
    }

    @Test
    void selectChildrenByParentIdExcludesInactiveLinksInactiveMembersAndOtherParentsChildren() {
        MemberVO parent = newMember("PARENT");
        memberMapper.insert(parent);
        MemberVO otherParent = newMember("PARENT");
        memberMapper.insert(otherParent);

        MemberVO unlinked = newMember("CHILD");
        memberMapper.insert(unlinked);
        MemberVO inactiveChild = newMember("CHILD");
        memberMapper.insert(inactiveChild);
        MemberVO othersChild = newMember("CHILD");
        memberMapper.insert(othersChild);

        link(parent.getId(), unlinked.getId(), "INACTIVE");
        link(parent.getId(), inactiveChild.getId(), "ACTIVE");
        jdbcTemplate.update(
                "UPDATE T_MBR_INFO_M SET status = 'INACTIVE' WHERE id = ?",
                inactiveChild.getId());
        link(otherParent.getId(), othersChild.getId(), "ACTIVE");

        assertTrue(memberMapper.selectChildrenByParentId(parent.getId()).isEmpty());
        assertEquals(1, memberMapper.selectChildrenByParentId(otherParent.getId()).size());
    }

    // --- selectActiveParentByChildId ---------------------------------------------

    @Test
    void selectActiveParentByChildIdReturnsLinkedParent() {
        MemberVO parent = newMember("PARENT");
        memberMapper.insert(parent);
        MemberVO child = newMember("CHILD");
        memberMapper.insert(child);
        link(parent.getId(), child.getId(), "ACTIVE");
        // 부모도 MEMBER 지갑을 갖는다. VO에 balance 필드가 없으므로 이 돈은 조회 경로에 오르지 않는다.
        wallet(parent.getId(), 509000L, "MEMBER");

        MemberParentVO found = memberMapper.selectActiveParentByChildId(child.getId());

        assertNotNull(found);
        // p.id AS parent_id 별칭이 빠지면 여기서 바로 걸린다.
        assertEquals(parent.getId(), found.getParentId());
        assertEquals(parent.getName(), found.getName());
    }

    @Test
    void selectActiveParentByChildIdReturnsNullWhenNoActiveLinkOrParentInactive() {
        MemberVO unlinked = newMember("CHILD");
        memberMapper.insert(unlinked);
        assertNull(memberMapper.selectActiveParentByChildId(unlinked.getId()),
                "연동한 적 없는 자녀는 null이어야 한다");

        MemberVO parent = newMember("PARENT");
        memberMapper.insert(parent);
        MemberVO released = newMember("CHILD");
        memberMapper.insert(released);
        link(parent.getId(), released.getId(), "INACTIVE");
        assertNull(memberMapper.selectActiveParentByChildId(released.getId()),
                "연동 해제된 관계는 null이어야 한다");

        MemberVO goneParent = newMember("PARENT");
        memberMapper.insert(goneParent);
        MemberVO orphan = newMember("CHILD");
        memberMapper.insert(orphan);
        link(goneParent.getId(), orphan.getId(), "ACTIVE");
        jdbcTemplate.update(
                "UPDATE T_MBR_INFO_M SET status = 'INACTIVE' WHERE id = ?", goneParent.getId());
        assertNull(memberMapper.selectActiveParentByChildId(orphan.getId()),
                "부모 계정이 비활성이면 null이어야 한다");
    }

    /**
     * INACTIVE 행은 UQ_MBR_CONN_R_ACTIVE_CHILD의 대상이 아니라 자녀당 여러 개 쌓일 수 있다.
     * conn.status 조건이 빠지면 다중 행이 되어 TooManyResultsException이 난다.
     */
    @Test
    void selectActiveParentByChildIdIgnoresMultipleInactiveLinks() {
        MemberVO first = newMember("PARENT");
        memberMapper.insert(first);
        MemberVO second = newMember("PARENT");
        memberMapper.insert(second);
        MemberVO current = newMember("PARENT");
        memberMapper.insert(current);
        MemberVO child = newMember("CHILD");
        memberMapper.insert(child);

        link(first.getId(), child.getId(), "INACTIVE");
        link(second.getId(), child.getId(), "INACTIVE");
        link(current.getId(), child.getId(), "ACTIVE");

        MemberParentVO found = memberMapper.selectActiveParentByChildId(child.getId());

        assertNotNull(found);
        assertEquals(current.getId(), found.getParentId());
    }

    private void link(Long parentId, Long childId, String status) {
        jdbcTemplate.update(
                "INSERT INTO T_MBR_CONN_R (parent_id, child_id, status) VALUES (?, ?, ?)",
                parentId, childId, status);
    }

    private void wallet(Long memberId, long balance, String type) {
        jdbcTemplate.update(
                "INSERT INTO T_WLT_BASE_M (member_id, balance, type) VALUES (?, ?, ?)",
                memberId, balance, type);
    }

    /** childId로 찾는다. c.id AS child_id 별칭이 빠지면 여기서 바로 걸린다. */
    private MemberChildVO byId(List<MemberChildVO> children, Long childId) {
        return children.stream()
                .filter(child -> childId.equals(child.getChildId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("자녀 " + childId + "가 목록에 없다"));
    }

}
