package com.teenyfin.teenymoney.domain.quest.service;

import com.teenyfin.teenymoney.domain.family.service.FamilyAccessService;
import com.teenyfin.teenymoney.domain.notification.mapper.MemberNotificationMapper;
import com.teenyfin.teenymoney.domain.notification.mapper.NotificationMapper;
import com.teenyfin.teenymoney.domain.notification.service.FcmService;
import com.teenyfin.teenymoney.domain.notification.service.NotificationService;
import com.teenyfin.teenymoney.domain.quest.dto.request.QuestCreateRequestDTO;
import com.teenyfin.teenymoney.domain.quest.dto.request.QuestRejectRequestDTO;
import com.teenyfin.teenymoney.domain.quest.mapper.QuestMapper;
import com.teenyfin.teenymoney.domain.quest.vo.QuestVerificationVO;
import com.teenyfin.teenymoney.domain.quest.vo.VerificationRequirement;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScoreChangeService;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScorePolicyService;
import com.teenyfin.teenymoney.domain.wallet.exception.WalletErrorCode;
import com.teenyfin.teenymoney.domain.wallet.service.TransferExecutor;
import com.teenyfin.teenymoney.domain.wallet.service.TransferFailureRecorder;
import com.teenyfin.teenymoney.domain.wallet.service.TransferService;
import com.teenyfin.teenymoney.domain.wallet.service.WalletLedgerService;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import com.teenyfin.teenymoney.global.storage.S3Storage;
import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 퀘스트 한 건의 생애를 실제 DB 위에서 끝까지 돌린다.
 *
 * 단위 테스트는 서비스마다 목으로 이웃을 잘라 놓아서, 생성이 만든 행을 수락이 정말 찾는지,
 * 승인 트랜잭션이 보상·점수·상태를 정말 함께 커밋하는지는 확인하지 못한다. 여기서는 목이
 * S3Storage 하나뿐이고 나머지는 전부 실제 빈과 실제 SQL 이다.
 *
 * 클래스에 @Transactional 을 붙이지 않는다. 붙이면 모든 것이 테스트 트랜잭션 안에서만
 * 존재하게 되어, 별도 트랜잭션으로 도는 마감 배치가 아무것도 보지 못한다. 대신 @Sql 정리
 * 스크립트가 뒷정리를 맡는다.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        QuestDeadlineTestConfig.class,
        QuestFlowIntegrationTest.FlowConfig.class,
        // 흐름에 참여하는 실제 빈을 하나씩 적는다. @ComponentScan 으로 패키지를 훑으면
        // 테스트 클래스패스에 같이 있는 테스트용 @Configuration 까지 잡힌다. 실제로
        // QuestDeadlineSavepointIntegrationTest.FailureConfig 가 이 패키지에 있고, 거기
        // 목 TeenyScoreChangeService 가 진짜 빈을 덮어써서 점수가 조용히 사라졌다.
        QuestCreationService.class,
        QuestProgressService.class,
        QuestReviewService.class,
        QuestDeadlineService.class,
        QuestStatePolicy.class,
        FamilyAccessService.class,
        TransferService.class,
        TransferExecutor.class,
        TransferFailureRecorder.class,
        WalletLedgerService.class,
        TeenyScorePolicyService.class,
        TeenyScoreChangeService.class
})
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".*(localhost|127\\.0\\.0\\.1).*")
@EnabledIfEnvironmentVariable(named = "DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@Sql(scripts = "/quest/setup-quest-flow-test.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/quest/cleanup-quest-flow-test.sql",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@DisplayName("퀘스트 전체 흐름 DB 통합 테스트")
class QuestFlowIntegrationTest {

    // 생성 API 가 childIds 를 양수로 검증해서 회원만 양수 대역을 쓴다(픽스처 주석 참고).
    private static final Long PARENT_ID = 900011L;
    private static final Long CHILD_ID = 900012L;
    private static final Long PARENT_WALLET_ID = -900011L;
    private static final Long CHILD_WALLET_ID = -900012L;

    private static final MemberPrincipal PARENT = new MemberPrincipal(PARENT_ID, "PARENT");
    private static final MemberPrincipal CHILD = new MemberPrincipal(CHILD_ID, "CHILD");

    /** 고정 시계가 2000-01-02 10:00 이므로 기한은 그 뒤로 잡는다. */
    private static final LocalDateTime DEADLINE = LocalDateTime.of(2000, 1, 3, 10, 0);

    @Autowired
    private QuestCreationService questCreationService;

    @Autowired
    private QuestProgressService questProgressService;

    @Autowired
    private QuestReviewService questReviewService;

    @Autowired
    private QuestDeadlineService questDeadlineService;

    @Autowired
    private QuestMapper questMapper;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("부모 생성 → 자녀 수락 → 인증 → 부모 승인 → 보상 지급까지 이어진다")
    void runsCreateAcceptSubmitApproveRewardFlow() {
        Long questId = createQuest(1000L, true);
        assertThat(questStatus(questId)).isEqualTo("AVAILABLE");

        questProgressService.accept(CHILD, questId);
        assertThat(questStatus(questId)).isEqualTo("IN_PROGRESS");

        questProgressService.submitVerification(CHILD, questId, "방 청소 다 했어요", null);
        assertThat(questStatus(questId)).isEqualTo("PENDING");

        QuestVerificationVO verification = questMapper.selectLatestVerification(questId);
        assertThat(verification.getAttemptNo()).isEqualTo(1);
        assertThat(verification.getStatus()).isEqualTo("PENDING");

        questReviewService.approve(PARENT, questId, verification.getId());

        assertThat(quest(questId))
                .containsEntry("status", "COMPLETED")
                .containsEntry("remaining_count", 3);
        assertThat(quest(questId).get("ended_at")).isNotNull();
        assertThat(questMapper.selectLatestVerification(questId).getStatus())
                .isEqualTo("APPROVED");

        // 보상은 부모 지갑에서 자녀 지갑으로 실제로 옮겨진다.
        assertThat(balance(PARENT_WALLET_ID)).isEqualTo(4000L);
        assertThat(balance(CHILD_WALLET_ID)).isEqualTo(1000L);
        assertThat(transferStatus(questId)).isEqualTo("COMPLETED");

        // 티니점수 퀘스트라 성공 +3 이 한 번 붙는다.
        assertThat(scoreAmounts("QUEST_COMPLETED:" + questId)).containsExactly(3);
        assertThat(childScore()).isEqualTo(603);

        // 각 단계의 알림이 실제로 T_NTF_NOTI_L 에 쌓인다.
        // 픽스처 회원은 fcm_token 이 NULL 이라 푸시 전송은 건너뛰고 이력만 남는다.
        assertThat(notifications(CHILD_ID)).extracting(row -> row.get("title"))
                .containsExactly("새 퀘스트가 도착했어요", "퀘스트 완료! 보상이 지급됐어요");
        assertThat(notifications(PARENT_ID)).extracting(row -> row.get("title"))
                .containsExactly("자녀가 퀘스트를 수락했어요", "인증을 확인해 주세요");

        Map<String, Object> created = notifications(CHILD_ID).get(0);
        assertThat(created.get("content")).isEqualTo("방 청소하기 · 보상 1,000원");
        assertThat(created.get("reference_type")).isEqualTo("QUEST");
        assertThat(((Number) created.get("reference_id")).longValue()).isEqualTo(questId);
        assertThat(created.get("is_read")).isEqualTo(Boolean.FALSE);
        assertThat(created.get("created_at")).isNotNull();

        // 부모에게 가는 알림에는 자녀 이름이 앞에 붙는다.
        assertThat(notifications(PARENT_ID).get(0).get("content"))
                .isEqualTo("흐름테스트자녀 · 방 청소하기");
    }

    @Test
    @DisplayName("반려 뒤 다시 제출한 인증을 승인하면 보상까지 지급된다")
    void allowsResubmissionAfterRejection() {
        Long questId = createQuest(1000L, false);
        questProgressService.accept(CHILD, questId);
        questProgressService.submitVerification(CHILD, questId, "1차 제출", null);

        Long firstVerificationId = questMapper.selectLatestVerification(questId).getId();
        questReviewService.reject(PARENT, questId, firstVerificationId,
                QuestRejectRequestDTO.builder().reason("사진이 잘 안 보여요").build());

        // 기회가 남았으므로 다시 수행 상태로 열린다. 종료가 아니다.
        assertThat(quest(questId))
                .containsEntry("status", "IN_PROGRESS")
                .containsEntry("remaining_count", 2);
        assertThat(quest(questId).get("ended_at")).isNull();

        questProgressService.submitVerification(CHILD, questId, "2차 제출", null);
        QuestVerificationVO second = questMapper.selectLatestVerification(questId);
        assertThat(second.getAttemptNo()).isEqualTo(2);
        assertThat(second.getId()).isNotEqualTo(firstVerificationId);

        questReviewService.approve(PARENT, questId, second.getId());

        assertThat(questStatus(questId)).isEqualTo("COMPLETED");
        assertThat(balance(CHILD_WALLET_ID)).isEqualTo(1000L);
        // 반려된 1차 인증은 그대로 남아 이력이 된다.
        assertThat(verificationStatuses(questId)).containsExactly("REJECTED", "APPROVED");
    }

    @Test
    @DisplayName("잔액이 모자라 승인이 실패하면 아무것도 바뀌지 않고, 충전 뒤 다시 승인하면 지급된다")
    void retriesApprovalAfterInsufficientBalance() {
        Long questId = createQuest(900000L, false);
        questProgressService.accept(CHILD, questId);
        questProgressService.submitVerification(CHILD, questId, "제출", null);
        Long verificationId = questMapper.selectLatestVerification(questId).getId();

        assertThatThrownBy(() -> questReviewService.approve(PARENT, questId, verificationId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(WalletErrorCode.INSUFFICIENT_BALANCE);

        // 승인 트랜잭션이 통째로 롤백되므로 접수된 송금 행까지 사라진다.
        assertThat(questStatus(questId)).isEqualTo("PENDING");
        assertThat(questMapper.selectLatestVerification(questId).getStatus())
                .isEqualTo("PENDING");
        assertThat(transferStatus(questId)).isNull();
        assertThat(balance(PARENT_WALLET_ID)).isEqualTo(5000L);
        assertThat(balance(CHILD_WALLET_ID)).isZero();

        jdbc().update("UPDATE T_WLT_BASE_M SET balance = ? WHERE id = ?",
                900000L, PARENT_WALLET_ID);

        questReviewService.approve(PARENT, questId, verificationId);

        assertThat(questStatus(questId)).isEqualTo("COMPLETED");
        assertThat(balance(PARENT_WALLET_ID)).isZero();
        assertThat(balance(CHILD_WALLET_ID)).isEqualTo(900000L);
        assertThat(transferStatus(questId)).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("남은 기회를 모두 반려하면 최종 실패로 확정되고 티니점수 -2가 한 번 붙는다")
    void endsAsFinalFailureAfterEveryAttemptIsRejected() {
        Long questId = createQuest(1000L, true);
        questProgressService.accept(CHILD, questId);

        for (int attempt = 1; attempt <= 3; attempt++) {
            questProgressService.submitVerification(CHILD, questId, attempt + "차 제출", null);
            Long verificationId = questMapper.selectLatestVerification(questId).getId();
            questReviewService.reject(PARENT, questId, verificationId,
                    QuestRejectRequestDTO.builder().reason("아직 부족해요").build());
        }

        assertThat(quest(questId))
                .containsEntry("status", "FAILED")
                .containsEntry("remaining_count", 0);
        assertThat(quest(questId).get("ended_at")).isNotNull();
        assertThat(verificationStatuses(questId))
                .containsExactly("REJECTED", "REJECTED", "REJECTED");

        assertThat(scoreAmounts("QUEST_FAILED:" + questId)).containsExactly(-2);
        assertThat(childScore()).isEqualTo(598);
        // 보상은 나가지 않는다.
        assertThat(balance(PARENT_WALLET_ID)).isEqualTo(5000L);
        assertThat(transferStatus(questId)).isNull();
    }

    @Test
    @DisplayName("수락한 채 기한이 지나면 배치가 FAILED로 마감하고 티니점수 -2를 한 번 붙인다")
    void closesAcceptedQuestByDeadlineBatch() {
        Long questId = createQuest(1000L, true);
        questProgressService.accept(CHILD, questId);

        // 시계를 옮기는 대신 기한을 과거로 민다. 배치가 보는 것은 deadline < now 하나뿐이라
        // 두 방식의 결과가 같고, 다른 테스트의 고정 시계를 건드리지 않는다.
        jdbc().update("UPDATE T_QST_BASE_M SET deadline = ? WHERE id = ?",
                LocalDateTime.of(2000, 1, 2, 8, 0), questId);

        questDeadlineService.closeExpired();

        assertThat(quest(questId))
                .containsEntry("status", "FAILED")
                .containsEntry("remaining_count", 0);
        assertThat(quest(questId).get("ended_at")).isNotNull();
        assertThat(scoreAmounts("QUEST_FAILED:" + questId)).containsExactly(-2);
        assertThat(childScore()).isEqualTo(598);

        // 두 번 돌아도 이미 종료 상태라 다시 잡히지 않는다.
        questDeadlineService.closeExpired();
        assertThat(scoreAmounts("QUEST_FAILED:" + questId)).containsExactly(-2);
    }

    private Long createQuest(long rewardAmount, boolean teenyScoreEnabled) {
        List<Long> questIds = questCreationService.create(
                PARENT,
                QuestCreateRequestDTO.builder()
                        .childIds(List.of(CHILD_ID))
                        .title("방 청소하기")
                        .content("책상과 바닥을 정리해 주세요.")
                        .deadline(DEADLINE)
                        .rewardAmount(rewardAmount)
                        .teenyScoreEnabled(teenyScoreEnabled)
                        .verificationRequirement(VerificationRequirement.FREE)
                        .build(),
                UUID.randomUUID().toString());
        assertThat(questIds).hasSize(1);
        return questIds.get(0);
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    /** 알림 이력을 생성 순서대로 읽는다. */
    private List<Map<String, Object>> notifications(Long memberId) {
        return jdbc().queryForList(
                "SELECT title, content, reference_type, reference_id, is_read, created_at "
                        + "FROM T_NTF_NOTI_L WHERE member_id = ? ORDER BY id",
                memberId);
    }

    private Map<String, Object> quest(Long questId) {
        return jdbc().queryForMap(
                "SELECT status, remaining_count, ended_at FROM T_QST_BASE_M WHERE id = ?",
                questId);
    }

    private String questStatus(Long questId) {
        return (String) quest(questId).get("status");
    }

    private Long balance(Long walletId) {
        return jdbc().queryForObject(
                "SELECT balance FROM T_WLT_BASE_M WHERE id = ?", Long.class, walletId);
    }

    /** 보상 송금은 questId 로 멱등키가 정해진다. 없으면 접수 자체가 남지 않았다는 뜻이다. */
    private String transferStatus(Long questId) {
        List<String> statuses = jdbc().queryForList(
                "SELECT status FROM T_WLT_TRF_L WHERE idempotency_key = ?",
                String.class, "QUEST_REWARD:" + questId);
        return statuses.isEmpty() ? null : statuses.get(0);
    }

    private List<String> verificationStatuses(Long questId) {
        return jdbc().queryForList(
                "SELECT status FROM T_QST_VERIFY_L WHERE quest_id = ? ORDER BY attempt_no",
                String.class, questId);
    }

    private List<Integer> scoreAmounts(String eventKey) {
        return jdbc().queryForList(
                "SELECT amount FROM T_TNY_SCOREHIST_H WHERE child_id = ? AND event_key = ?",
                Integer.class, CHILD_ID, eventKey);
    }

    private Integer childScore() {
        return jdbc().queryForObject(
                "SELECT teeny_score FROM T_MBR_INFO_M WHERE id = ?", Integer.class, CHILD_ID);
    }

    /**
     * DataSource·SqlSessionFactory·Clock·트랜잭션 매니저는 QuestDeadlineTestConfig 가 준다.
     * 여기서는 그쪽이 훑지 않는 매퍼와, 목 두 개만 더한다.
     */
    @Configuration
    @MapperScan(
            basePackages = {
                    "com.teenyfin.teenymoney.domain.member.mapper",
                    "com.teenyfin.teenymoney.domain.wallet.mapper",
                    "com.teenyfin.teenymoney.domain.notification.mapper"
            },
            annotationClass = Mapper.class)
    static class FlowConfig {

        /** 인증은 전부 글로만 제출한다. 업로드 경로를 타지 않으므로 호출되지 않는다. */
        @Bean
        S3Storage s3Storage() {
            return mock(S3Storage.class);
        }

        /**
         * 알림도 실제 빈을 쓴다. T_NTF_NOTI_L 에 진짜로 적재되는지가 확인 대상이다.
         *
         * FcmService 만 목이다. 진짜를 쓰면 FirebaseApp 초기화(S3에서 키를 받아 온다)까지
         * 딸려 오는데, 여기서 볼 것은 푸시 전송이 아니라 DB 적재다.
         * 픽스처 회원은 fcm_token 이 NULL 이라 어차피 발송 경로를 타지도 않는다.
         */
        @Bean
        FcmService fcmService() {
            return mock(FcmService.class);
        }

        @Bean
        NotificationService notificationService(NotificationMapper notificationMapper,
                                                MemberNotificationMapper memberNotificationMapper,
                                                FcmService fcmService) {
            return new NotificationService(notificationMapper, memberNotificationMapper, fcmService);
        }
    }
}
