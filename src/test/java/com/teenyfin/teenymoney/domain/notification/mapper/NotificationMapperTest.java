package com.teenyfin.teenymoney.domain.notification.mapper;

import com.teenyfin.teenymoney.config.LazyBeanInitializer;
import com.teenyfin.teenymoney.config.RootConfig;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// LazyBeanInitializer를 붙이는 이유: RootConfig가 도메인 전체를 스캔하다가 TossPaymentsClient
// (RestTemplate 빈 필요 - RestTemplateConfig는 이 좁은 테스트 컨텍스트엔 없음)까지 즉시 만들려다
// 실패하는 걸 막아준다. ChargeServiceTest/AllowanceServiceTest와 같은 이유.
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RootConfig.class, initializers = LazyBeanInitializer.class)
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
public class NotificationMapperTest {

    @Autowired
    private NotificationMapper notificationMapper;

    @Autowired
    private MemberMapper memberMapper;

    private JdbcTemplate jdbcTemplate;

    private Long memberId;

    @Autowired
    void setDataSource(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void setUp() {
        memberId = insertMember();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM T_NTF_NOTI_L WHERE member_id = ?", memberId);
        jdbcTemplate.update("DELETE FROM T_MBR_INFO_M WHERE id = ?", memberId);
    }

    private Long insertMember() {
        String unique = UUID.randomUUID().toString().replace("-", "");
        int phoneSuffix = Math.floorMod(unique.hashCode(), 100_000_000);

        MemberVO member = new MemberVO();
        member.setRole("CHILD");
        member.setName("알림매퍼테스트");
        member.setBirthDate(LocalDate.of(2010, 1, 1));
        member.setPhoneNumber(String.format("010%08d", phoneSuffix));
        member.setEmail("notification-mapper-" + unique + "@test.local");
        member.setPassword("$2a$10$test-only-hash");
        memberMapper.insert(member);
        return member.getId();
    }

    @Test
    void insertAssignsGeneratedIdAndPersistsAllColumns() {
        NotificationVO notificationVO = NotificationVO.builder()
                .memberId(memberId)
                .title("결제가 완료됐어요")
                .content("GS25 강남점 · 3,200원")
                .referenceType(NotificationReferenceType.PAYMENT)
                .referenceId(99L)
                .build();

        notificationMapper.insert(notificationVO);

        System.out.println("[INSERT] id=" + notificationVO.getId());
        assertNotNull(notificationVO.getId());

        // then: 매퍼를 거치지 않고 컬럼을 직접 읽어서, 진짜로 DB에 반영됐는지 확인한다
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT member_id, title, content, reference_type, reference_id, is_read, created_at " +
                        "FROM T_NTF_NOTI_L WHERE id = ?", notificationVO.getId());
        System.out.println("[ROW] " + row);

        assertEquals(memberId, ((Number) row.get("member_id")).longValue());
        assertEquals("결제가 완료됐어요", row.get("title"));
        assertEquals("GS25 강남점 · 3,200원", row.get("content"));
        assertEquals("PAYMENT", row.get("reference_type"));
        assertEquals(99L, ((Number) row.get("reference_id")).longValue());
        // is_read/created_at은 insert 문에서 안 채우고 DB 기본값에 맡긴다
        assertEquals(Boolean.FALSE, row.get("is_read"));
        assertNotNull(row.get("created_at"));
    }

    @Test
    void insertAllowsNullReferenceId() {
        NotificationVO notificationVO = NotificationVO.builder()
                .memberId(memberId)
                .title("공지")
                .content("점검 안내")
                .referenceType(null)
                .referenceId(null)
                .build();

        notificationMapper.insert(notificationVO);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT reference_type, reference_id FROM T_NTF_NOTI_L WHERE id = ?", notificationVO.getId());
        System.out.println("[ROW] " + row);

        assertNull(row.get("reference_type"));
        assertNull(row.get("reference_id"));
    }

    @Test
    void selectRecentNotificationsPagesThroughCursorNewestFirst() {
        // given: 알림 15건을 순서대로 삽입 (insert 순서 = id 오름차순)
        List<Long> insertedIds = new java.util.ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            NotificationVO notificationVO = NotificationVO.builder()
                    .memberId(memberId)
                    .title("알림" + i)
                    .content("내용" + i)
                    .referenceType(NotificationReferenceType.PAYMENT)
                    .referenceId((long) i)
                    .build();
            notificationMapper.insert(notificationVO);
            insertedIds.add(notificationVO.getId());
        }

        // when: 첫 페이지 - 11건(FETCH_SIZE) 요청
        List<NotificationVO> firstPage = notificationMapper.selectRecentNotifications(memberId, null, null, 11);
        System.out.println("[FIRST PAGE] ids=" + firstPage.stream().map(NotificationVO::getId).toList());

        // then: 최신순(가장 나중에 넣은 id부터) 11건이 온다
        assertEquals(11, firstPage.size());
        for (int i = 0; i < 11; i++) {
            assertEquals(insertedIds.get(14 - i), firstPage.get(i).getId());
        }

        // when: 마지막 항목을 커서로 다음 페이지 요청
        NotificationVO cursorRow = firstPage.get(10);
        List<NotificationVO> secondPage = notificationMapper.selectRecentNotifications(
                memberId, cursorRow.getCreatedAt(), cursorRow.getId(), 11);
        System.out.println("[SECOND PAGE] ids=" + secondPage.stream().map(NotificationVO::getId).toList());

        // then: 나머지 4건이 이어서 온다 (중복도 누락도 없이)
        assertEquals(4, secondPage.size());
        for (int i = 0; i < 4; i++) {
            assertEquals(insertedIds.get(3 - i), secondPage.get(i).getId());
        }
    }
}