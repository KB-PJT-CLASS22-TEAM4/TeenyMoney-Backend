package com.teenyfin.teenymoney.domain.allowance.service;

import com.teenyfin.teenymoney.config.LazyBeanInitializer;
import com.teenyfin.teenymoney.config.RestTemplateConfig;
import com.teenyfin.teenymoney.config.RootConfig;
import com.teenyfin.teenymoney.domain.allowance.dto.response.AllowanceSendResponseDTO;
import com.teenyfin.teenymoney.domain.family.service.FamilyAccessService;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.notification.service.NotificationService;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import com.teenyfin.teenymoney.domain.wallet.exception.WalletErrorCode;
import com.teenyfin.teenymoney.domain.wallet.mapper.TransferMapper;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.service.TransferExecutor;
import com.teenyfin.teenymoney.domain.wallet.service.TransferFailureRecorder;
import com.teenyfin.teenymoney.domain.wallet.service.TransferService;
import com.teenyfin.teenymoney.domain.wallet.service.WalletLedgerService;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.exception.CommonErrorCode;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

// @ExtendWith(SpringExtension.class): JUnit5한테 "이 테스트는 Spring이 관리하는 빈(Bean)들을
// 써야 하니, 테스트 실행 전에 Spring 컨텍스트(설정)를 띄워달라"고 연결해주는 어댑터입니다.
@ExtendWith(SpringExtension.class)
// RootConfig에 등록된 빈들(DataSource, MyBatis 매퍼들)만 띄웁니다. AllowanceService 자체는
// @Service라서 이 root 전용 컨텍스트엔 없기 때문에, 아래 setUp()에서 직접 new로 조립합니다.
@ContextConfiguration(classes = {RootConfig.class, RestTemplateConfig.class}, initializers = LazyBeanInitializer.class)
// 이 3개의 환경변수(DB_URL/DB_USERNAME/DB_PASSWORD)가 없으면 이 클래스 전체가 "건너뛰기"
// 처리됩니다. 그래야 로컬 DB 설정 없이 ./gradlew test 돌리는 사람 PC에서도 빌드가 깨지지 않습니다.
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
// 클래스에 @Transactional을 일부러 안 붙입니다 - 위에서 설명한 이유(executeTransfer가
// NOT_SUPPORTED라 테스트 트랜잭션을 정지시키고 진짜로 커밋해버리기 때문)입니다.
// 그래서 대신 @AfterEach에서 jdbcTemplate으로 직접 지웁니다.
public class AllowanceServiceTest {

    // @Autowired: Spring 컨텍스트에 등록된 진짜 MemberMapper/WalletMapper/TransferMapper
    // 빈을 그대로 주입받습니다. 이 매퍼들은 실제 MyBatis XML을 거쳐 진짜 DB에 SQL을 날립니다.
    @Autowired
    private MemberMapper memberMapper;

    @Autowired
    private WalletMapper walletMapper;

    @Autowired
    private TransferMapper transferMapper;

    // RootConfig는 JdbcTemplate 빈을 따로 등록 안 해서(프로덕션 코드가 안 쓰니까),
    // 테스트에서만 쓸 용도로 DataSource를 받아 직접 만듭니다.
    private JdbcTemplate jdbcTemplate;

    // 테스트 대상. AllowanceService는 컨텍스트에 빈으로 없으니, 의존하는 것들을 전부
    // 손으로 new해서 직접 조립합니다 (TransferServiceTest와 같은 방식).
    private AllowanceService allowanceService;

    // 알림이 실제로 나갔는지(누구한테, 무슨 내용으로)만 확인할 용도라 mock으로 둡니다.
    // 진짜 FCM/DB까지 태울 필요는 없습니다.
    private NotificationService notificationService;

    // 매 테스트마다 새로 만드는 부모/자녀 회원 id, 부모 지갑 id.
    // 자녀 지갑 id는 테스트마다 필요할 때만 만들어서 별도로 관리합니다(아래 참고).
    private Long parentId;
    private Long childId;
    private Long parentWalletId;
    private Long childWalletId;      // 지갑이 필요 없는 테스트(WALLET_NOT_FOUND용)에서는 null로 남음
    private Long otherChildId;       // "내 자녀 아님" 테스트에서만 쓰는 남남 자녀. 기본은 null

    @Autowired
    void setDataSource(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void setUp() {
        // AllowanceService가 실제로 생성자에서 받는 3가지를 그대로 손으로 조립합니다.
        // (AllowanceController가 스프링한테 주입받는 걸, 여기선 우리가 직접 해주는 셈)
        FamilyAccessService familyAccessService = new FamilyAccessService(memberMapper);
        WalletLedgerService walletLedgerService = new WalletLedgerService(walletMapper);
        TransferExecutor transferExecutor = new TransferExecutor(transferMapper, walletLedgerService);
        TransferFailureRecorder transferFailureRecorder = new TransferFailureRecorder(transferMapper);
        notificationService = mock(NotificationService.class);
        TransferService transferService = new TransferService(transferMapper, transferExecutor, transferFailureRecorder, walletMapper, notificationService, event -> {});
        allowanceService = new AllowanceService(familyAccessService, walletMapper, transferService);

        // 부모 1명 + 자녀 1명을 만들고, 가족 연동(T_MBR_CONN_R)까지 ACTIVE로 걸어둡니다.
        // 이게 모든 테스트의 공통 전제(부모-자녀가 이미 연동된 상태)입니다.
        parentId = insertMember("PARENT");
        childId = insertMember("CHILD");
        link(parentId, childId, "ACTIVE");

        // 부모 지갑은 모든 테스트에 공통으로 필요하니 여기서 만듭니다. 잔액 50000원.
        parentWalletId = insertWallet(parentId, 50000L, "MEMBER");

        System.out.println("[SETUP] parentId=" + parentId + ", childId=" + childId
                + ", parentWalletId=" + parentWalletId + "(50000원)");
    }

    // 자녀 지갑이 필요한 테스트에서만 이걸 호출해서 만듭니다.
    private void createChildWallet(long balance) {
        childWalletId = insertWallet(childId, balance, "MEMBER");
        System.out.println("[SETUP] childWalletId=" + childWalletId + "(" + balance + "원)");
    }

    @AfterEach
    void tearDown() {
        // FK(ON DELETE RESTRICT)가 걸려있어서, 참조하는 쪽부터(원장 -> 송금기록 -> 연동 ->
        // 지갑 -> 회원) 역순으로 지워야 합니다. childWalletId/otherChildId는 테스트에 따라
        // null일 수 있어서 null이면 건너뜁니다.
        jdbcTemplate.update("DELETE FROM T_WLT_HIST_H WHERE wallet_id IN (?, ?)",
                parentWalletId, childWalletId == null ? -1 : childWalletId);
        jdbcTemplate.update("DELETE FROM T_WLT_TRF_L WHERE from_wallet_id IN (?, ?) OR to_wallet_id IN (?, ?)",
                parentWalletId, childWalletId == null ? -1 : childWalletId,
                parentWalletId, childWalletId == null ? -1 : childWalletId);
        jdbcTemplate.update("DELETE FROM T_MBR_CONN_R WHERE parent_id = ? AND child_id = ?", parentId, childId);
        if (childWalletId != null) {
            jdbcTemplate.update("DELETE FROM T_WLT_BASE_M WHERE id = ?", childWalletId);
        }
        jdbcTemplate.update("DELETE FROM T_WLT_BASE_M WHERE id = ?", parentWalletId);
        jdbcTemplate.update("DELETE FROM T_MBR_INFO_M WHERE id = ?", childId);
        jdbcTemplate.update("DELETE FROM T_MBR_INFO_M WHERE id = ?", parentId);
        if (otherChildId != null) {
            jdbcTemplate.update("DELETE FROM T_MBR_INFO_M WHERE id = ?", otherChildId);
        }
    }

    // ---------- 여기서부터 실제 테스트 ----------

    @Test
    void sendAllowanceMovesBalanceAndReturnsCompletedResponse() {
        // given: 자녀도 지갑이 있는 정상 상태 (0원에서 시작)
        createChildWallet(0L);
        MemberPrincipal parentPrincipal = new MemberPrincipal(parentId, "PARENT");

        // when
        AllowanceSendResponseDTO response = allowanceService.sendAllowance(
                parentPrincipal, childId, 10000L, UUID.randomUUID().toString());

        System.out.println("[RESULT] transferId=" + response.getTransferId()
                + ", status=" + response.getStatus() + ", amount=" + response.getAmount());

        // then: 응답 DTO 확인
        assertNotNull(response.getTransferId());
        assertEquals("COMPLETED", response.getStatus());
        assertEquals(10000L, response.getAmount());

        // then: 실제 잔액도 옮겨졌는지 DB에서 직접 확인 (부모 -10000, 자녀 +10000)
        Long parentBalance = walletMapper.selectMemberWalletByMemberId(parentId).getBalance();
        Long childBalance = walletMapper.selectMemberWalletByMemberId(childId).getBalance();
        System.out.println("[AFTER] parentBalance=" + parentBalance + ", childBalance=" + childBalance);

        assertEquals(40000L, parentBalance);
        assertEquals(10000L, childBalance);

        // then: 자녀한테 "용돈이 입금 됐어요" 알림이 갔는지도 확인
        verify(notificationService).createNotification(
                eq(childId), eq("용돈이 입금 됐어요"), eq("10,000원"),
                eq(NotificationReferenceType.TRANSFER), eq((Long) null), eq(true));
    }

    @Test
    void sendAllowanceThrowsWalletNotFoundWhenChildHasNoWallet() {
        // given: createChildWallet()을 일부러 호출 안 함 -> 자녀는 연동은 됐지만 지갑이 없는 상태
        MemberPrincipal parentPrincipal = new MemberPrincipal(parentId, "PARENT");

        // 이 테스트가 사실 지난번에 고친 버그의 회귀 테스트이기도 합니다.
        // 예전 코드는 자녀 지갑을 조회할 때 childId 대신 principal.memberId()(부모 자신)를 써서,
        // 자녀 지갑이 없어도 부모 자신의 지갑이 조회돼 버그가 조용히 통과했었습니다.
        BusinessException exception = assertThrows(BusinessException.class,
                () -> allowanceService.sendAllowance(parentPrincipal, childId, 10000L, UUID.randomUUID().toString()));

        System.out.println("[EXCEPTION] " + exception.getErrorCode());
        assertEquals(WalletErrorCode.WALLET_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void sendAllowanceThrowsForbiddenWhenChildIsNotLinkedToParent() {
        // given: 이 부모와 연동되지 않은 "남의 자녀"를 하나 새로 만듭니다.
        otherChildId = insertMember("CHILD");
        MemberPrincipal parentPrincipal = new MemberPrincipal(parentId, "PARENT");

        // when & then: FamilyAccessService.requireChildAccess()가 첫 줄에서 막아야 합니다.
        BusinessException exception = assertThrows(BusinessException.class,
                () -> allowanceService.sendAllowance(parentPrincipal, otherChildId, 10000L, UUID.randomUUID().toString()));

        System.out.println("[EXCEPTION] " + exception.getErrorCode());
        assertEquals(CommonErrorCode.AUTH_FORBIDDEN, exception.getErrorCode());
    }

    @Test
    void sendAllowanceThrowsInsufficientBalanceWhenParentBalanceTooLow() {
        // given: 부모 지갑엔 50000원뿐인데, 그보다 훨씬 큰 금액을 보내려고 시도
        createChildWallet(0L);
        MemberPrincipal parentPrincipal = new MemberPrincipal(parentId, "PARENT");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> allowanceService.sendAllowance(parentPrincipal, childId, 999_999_999L, UUID.randomUUID().toString()));

        System.out.println("[EXCEPTION] " + exception.getErrorCode());
        assertEquals(WalletErrorCode.INSUFFICIENT_BALANCE, exception.getErrorCode());

        // then: 실패했으니 부모 잔액은 그대로 50000원이어야 한다 (돈이 반쯤 빠져나가면 안 됨)
        Long parentBalance = walletMapper.selectMemberWalletByMemberId(parentId).getBalance();
        System.out.println("[AFTER] parentBalance=" + parentBalance + " (변화 없어야 함)");
        assertEquals(50000L, parentBalance);
    }

    // ---------- 여기부터 헬퍼 (MemberMapperTest의 newMember/link/wallet과 같은 패턴) ----------

    private Long insertMember(String role) {
        String unique = UUID.randomUUID().toString().replace("-", "");
        int phoneSuffix = Math.floorMod(unique.hashCode(), 100_000_000);

        MemberVO member = new MemberVO();
        member.setRole(role);
        member.setName("용돈테스트");
        member.setBirthDate(LocalDate.of(2010, 1, 2));
        member.setPhoneNumber(String.format("010%08d", phoneSuffix));
        member.setEmail("allowance-" + unique + "@test.local");
        member.setPassword("$2a$10$test-only-hash");
        memberMapper.insert(member);
        return member.getId();
    }

    private void link(Long parentId, Long childId, String status) {
        jdbcTemplate.update(
                "INSERT INTO T_MBR_CONN_R (parent_id, child_id, status) VALUES (?, ?, ?)",
                parentId, childId, status);
    }

    private Long insertWallet(Long memberId, long balance, String type) {
        jdbcTemplate.update(
                "INSERT INTO T_WLT_BASE_M (member_id, balance, type) VALUES (?, ?, ?)",
                memberId, balance, type);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
