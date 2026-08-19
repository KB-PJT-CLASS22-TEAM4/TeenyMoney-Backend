package com.teenyfin.teenymoney.domain.notification.mapper;

import com.teenyfin.teenymoney.config.LazyBeanInitializer;
import com.teenyfin.teenymoney.config.RootConfig;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import com.teenyfin.teenymoney.domain.notification.vo.MemberNotificationVO;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// LazyBeanInitializer를 붙이는 이유: NotificationMapperTest와 같다 (RootConfig의 전체 컴포넌트
// 스캔이 RestTemplate 빈 없는 TossPaymentsClient까지 즉시 만들려는 걸 막는다).
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RootConfig.class, initializers = LazyBeanInitializer.class)
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
public class MemberNotificationMapperTest {

    @Autowired
    private MemberNotificationMapper memberNotificationMapper;

    @Autowired
    private MemberMapper memberMapper;

    private JdbcTemplate jdbcTemplate;

    private Long memberId;
    private Long otherMemberId;

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
        jdbcTemplate.update("DELETE FROM T_MBR_INFO_M WHERE id = ?", memberId);
        if (otherMemberId != null) {
            jdbcTemplate.update("DELETE FROM T_MBR_INFO_M WHERE id = ?", otherMemberId);
            otherMemberId = null;
        }
    }

    private Long insertMember() {
        String unique = UUID.randomUUID().toString().replace("-", "");
        int phoneSuffix = Math.floorMod(unique.hashCode(), 100_000_000);

        MemberVO member = new MemberVO();
        member.setRole("CHILD");
        member.setName("회원알림매퍼테스트");
        member.setBirthDate(LocalDate.of(2010, 1, 1));
        member.setPhoneNumber(String.format("010%08d", phoneSuffix));
        member.setEmail("member-notification-mapper-" + unique + "@test.local");
        member.setPassword("$2a$10$test-only-hash");
        memberMapper.insert(member);
        return member.getId();
    }

    @Test
    void selectNotificationInfoReturnsDefaultsRightAfterSignup() {
        // then: 가입 직후엔 fcm_token은 NULL, 알림 채널은 스키마 기본값(전부 TRUE)이어야 한다
        MemberNotificationVO info = memberNotificationMapper.selectNotificationInfo(memberId);
        System.out.println("[DEFAULT] " + info);

        assertNotNull(info);
        assertNull(info.getFcmToken());
        assertEquals(Boolean.TRUE, info.getNotiPayment());
        assertEquals(Boolean.TRUE, info.getNotiQuest());
        assertEquals(Boolean.TRUE, info.getNotiFinance());
        assertEquals(Boolean.TRUE, info.getNotiAllowance());
    }

    @Test
    void selectNotificationInfoReflectsUpdatedFcmTokenAndChannelFlags() {
        jdbcTemplate.update(
                "UPDATE T_MBR_INFO_M SET fcm_token = ?, noti_payment = FALSE, noti_quest = FALSE " +
                        "WHERE id = ?",
                "real-fcm-token", memberId);

        MemberNotificationVO info = memberNotificationMapper.selectNotificationInfo(memberId);
        System.out.println("[UPDATED] " + info);

        assertEquals("real-fcm-token", info.getFcmToken());
        assertEquals(Boolean.FALSE, info.getNotiPayment());
        assertEquals(Boolean.FALSE, info.getNotiQuest());
        // 안 건드린 채널은 그대로 TRUE여야 한다
        assertEquals(Boolean.TRUE, info.getNotiFinance());
        assertEquals(Boolean.TRUE, info.getNotiAllowance());
    }

    @Test
    void selectNotificationInfoReturnsNullForUnknownMember() {
        MemberNotificationVO info = memberNotificationMapper.selectNotificationInfo(999_999_999L);
        System.out.println("[UNKNOWN] " + info);

        assertNull(info);
    }

    @Test
    void updateFcmTokenMovesOwnershipAwayFromThePreviousMember() {
        otherMemberId = insertMember();

        // 같은 기기(=같은 토큰)에서 계정을 갈아탄 상황
        memberNotificationMapper.updateFcmToken(memberId, "shared-device-token");
        memberNotificationMapper.updateFcmToken(otherMemberId, "shared-device-token");

        // 마지막에 로그인한 회원만 토큰을 갖고, 이전 회원 행은 비워져야 한다.
        // 안 비우면 이전 회원의 푸시가 지금 그 기기를 쓰는 사람에게 간다.
        assertNull(memberNotificationMapper.selectNotificationInfo(memberId).getFcmToken());
        assertEquals("shared-device-token",
                memberNotificationMapper.selectNotificationInfo(otherMemberId).getFcmToken());
    }

    @Test
    void updateFcmTokenWithNullClearsOnlyThatMember() {
        otherMemberId = insertMember();

        memberNotificationMapper.updateFcmToken(memberId, "logout-token");
        memberNotificationMapper.updateFcmToken(otherMemberId, "another-device-token");

        // 로그아웃 경로. null이 들어와도 매퍼가 터지지 않아야 하고,
        // 남의 토큰까지 쓸어가서는 안 된다.
        memberNotificationMapper.updateFcmToken(memberId, null);

        assertNull(memberNotificationMapper.selectNotificationInfo(memberId).getFcmToken());
        assertEquals("another-device-token",
                memberNotificationMapper.selectNotificationInfo(otherMemberId).getFcmToken());
    }
}
