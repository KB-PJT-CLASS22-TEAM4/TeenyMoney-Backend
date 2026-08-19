package com.teenyfin.teenymoney.domain.wallet.service;


import com.teenyfin.teenymoney.config.LazyBeanInitializer;
import com.teenyfin.teenymoney.config.RestTemplateConfig;
import com.teenyfin.teenymoney.config.RootConfig;
import com.teenyfin.teenymoney.domain.notification.service.NotificationService;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;
import com.teenyfin.teenymoney.domain.wallet.exception.WalletErrorCode;
import com.teenyfin.teenymoney.domain.wallet.mapper.TransferMapper;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.vo.TransferType;
import com.teenyfin.teenymoney.domain.wallet.vo.TransferVO;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

// 클래스에 @Transactional을 일부러 안 붙인다 - createPendingTransfer()가
// 진짜로 독립적으로 커밋되는지, executeTransfer()가 실패해도 그 행이 안 사라지는지를
// 검증하려면 각 단계가 실제로 커밋돼야 하기 때문 (AuthServiceTransactionIntegrationTest와 같은 이유).
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {RootConfig.class, RestTemplateConfig.class}, initializers = LazyBeanInitializer.class)
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
public class TransferServiceTest {

    @Autowired
    private WalletMapper walletMapper;

    @Autowired
    private TransferMapper transferMapper;

    private JdbcTemplate jdbcTemplate;

    // WalletLedgerService/TransferService는 @Service라서 이 root-only 컨텍스트엔 빈으로 없다.
    // 진짜 매퍼를 직접 new로 꽂아서 만든다 (WalletServiceCreateWalletTest와 같은 방식).
    private TransferService transferService;

    // 알림 발송 자체(FCM/DB)는 이 테스트의 관심사가 아니라서 mock으로 둔다.
    // "알림이 몇 번 불렸는지/누구한테 불렸는지"만 검증하는 용도.
    private NotificationService notificationService;

    //테스트 전용 지갑 두개(매 테스트 마다 새로 만들고 끝나면 삭제)
    private Long fromWalletId;
    private Long toWalletId;

    @Autowired
    void setDataSource(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void setUp() {
        WalletLedgerService walletLedgerService = new WalletLedgerService(walletMapper);
        TransferExecutor transferExecutor = new TransferExecutor(transferMapper, walletLedgerService);
        TransferFailureRecorder transferFailureRecorder = new TransferFailureRecorder(transferMapper);
        notificationService = mock(NotificationService.class);
        transferService = new TransferService(transferMapper, transferExecutor, transferFailureRecorder, walletMapper, notificationService, event -> {});

        //테스트 전용 지갑 생성
        fromWalletId = insertWallet(2L, 100000L);
        toWalletId = insertWallet(2L, 50000L);
        System.out.println("[SETUP] fromWalletId=" + fromWalletId + "(100000원), toWalletId=" + toWalletId + "(50000원)");
    }

    private Long insertWallet(Long memberId, Long balance) {
        jdbcTemplate.update(
                "INSERT INTO T_WLT_BASE_M (member_id, balance, type) VALUES (?, ?, 'SAVING')",
                 memberId, balance);

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @AfterEach
    void tearDown() {
        // FK(ON DELETE RESTRICT) 때문에 지갑보다 먼저 지워야 하는 것들:
        jdbcTemplate.update(
                "DELETE FROM T_WLT_HIST_H WHERE wallet_id IN (?, ?)", fromWalletId, toWalletId);
        jdbcTemplate.update(
                "DELETE FROM T_WLT_TRF_L WHERE from_wallet_id IN (?, ?) OR to_wallet_id IN (?, ?)",
                fromWalletId, toWalletId, fromWalletId, toWalletId);
        jdbcTemplate.update(
                "DELETE FROM T_WLT_BASE_M WHERE id IN (?, ?)", fromWalletId, toWalletId);
    }

    private String newIdempotencyKey() {
        return UUID.randomUUID().toString();
    }

    @Test
    void createPendingTransferInsertsRowWithPendingStatus() {
        String idempotencyKey = newIdempotencyKey();

        TransferVO pending = transferService.createPendingTransfer(fromWalletId, toWalletId, 10000L, TransferType.TRANSFER, idempotencyKey);
        System.out.println("[CREATE] transferId=" + pending.getId() + ", status=" + pending.getStatus());

        assertNotNull(pending.getId());
        assertEquals("PENDING", pending.getStatus());

        TransferVO stored = transferMapper.selectByIdempotencyKey(idempotencyKey);
        assertNotNull(stored);
        assertEquals(pending.getId(), stored.getId());
    }

    @Test
    void createPendingTransferReturnsSameRowWhenIdempotencyKeyReused() {
        String idempotencyKey = newIdempotencyKey();

        TransferVO first = transferService.createPendingTransfer(
                fromWalletId, toWalletId, 10000L, TransferType.TRANSFER, idempotencyKey);

        TransferVO second = transferService.createPendingTransfer(
                fromWalletId, toWalletId, 10000L, TransferType.TRANSFER, idempotencyKey);

        System.out.println("[IDEMPOTENCY] first.id=" + first.getId() + ", second.id=" + second.getId());
        assertEquals(first.getId(), second.getId());
    }

    @Test
    void createPendingTransferThrowsWhenFromAndToWalletAreSame() {
        BusinessException exception = assertThrows(BusinessException.class, () -> transferService.createPendingTransfer(
                fromWalletId, fromWalletId, 10000L, TransferType.TRANSFER, newIdempotencyKey()));

        assertEquals(WalletErrorCode.TRANSFER_SAME_WALLET, exception.getErrorCode());
        System.out.println("[EXCEPTION] " + exception.getErrorCode());

    }

    @Test
    void executeTransferMovesBalanceAndMarksCompletedWhenFromLessThanTo() {
        // given: fromWalletId < toWalletId가 되도록 생성 순서에 의존하지 않으려고, 실제 값 비교로 분기 확인
        Long smaller = Math.min(fromWalletId, toWalletId);
        Long larger = Math.max(fromWalletId, toWalletId);

        // 송금 전 잔액을 먼저 실제로 조회해둔다 - smaller/larger가 실제로 어느 지갑이든 상관없이
        // "±amount만큼 정확히 옮겨졌는지"만 검증하기 위함.
        Long smallerBalanceBefore = walletMapper.selectWalletForUpdate(smaller).getBalance();
        Long largerBalanceBefore = walletMapper.selectWalletForUpdate(larger).getBalance();
        System.out.println("[BEFORE] smaller(from)=" + smallerBalanceBefore + ", larger(to)=" + largerBalanceBefore);

        TransferVO pending = transferService.createPendingTransfer(
                smaller, larger, 10000L, TransferType.TRANSFER, newIdempotencyKey());

        TransferVO result = transferService.executeTransfer(pending.getId());
        System.out.println("[EXECUTE] status=" + result.getStatus());
        assertEquals("COMPLETED", result.getStatus());

        WalletVO smallerWallet = walletMapper.selectWalletForUpdate(smaller);
        WalletVO largerWallet = walletMapper.selectWalletForUpdate(larger);
        System.out.println("[AFTER] smaller(from)=" + smallerWallet.getBalance() + ", larger(to)=" + largerWallet.getBalance());

        // then: smaller(from)는 10000원 줄고, larger(to)는 10000원 늘어야 한다
        assertEquals(smallerBalanceBefore - 10000L, smallerWallet.getBalance());
        assertEquals(largerBalanceBefore + 10000L, largerWallet.getBalance());
    }

    @Test
    void executeTransferMovesBalanceAndMarksCompletedWhenFromGreaterThanTo() {
        // given: 반대 방향 - fromWalletId > toWalletId (잠금 순서 로직의 반대 분기를 태운다)
        Long smaller = Math.min(fromWalletId, toWalletId);
        Long larger = Math.max(fromWalletId, toWalletId);

        Long largerBalanceBefore = walletMapper.selectWalletForUpdate(larger).getBalance();
        Long smallerBalanceBefore = walletMapper.selectWalletForUpdate(smaller).getBalance();
        System.out.println("[BEFORE] larger(from)=" + largerBalanceBefore + ", smaller(to)=" + smallerBalanceBefore);

        TransferVO pending = transferService.createPendingTransfer(
                larger, smaller, 5000L, TransferType.TRANSFER, newIdempotencyKey());

        TransferVO result = transferService.executeTransfer(pending.getId());
        System.out.println("[EXECUTE] status=" + result.getStatus());
        assertEquals("COMPLETED", result.getStatus());

        WalletVO fromWallet = walletMapper.selectWalletForUpdate(larger);
        WalletVO toWallet = walletMapper.selectWalletForUpdate(smaller);
        System.out.println("[AFTER] from=" + fromWallet.getBalance() + ", to=" + toWallet.getBalance());

        // then: larger(from)는 5000원 줄고, smaller(to)는 5000원 늘어야 한다
        assertEquals(largerBalanceBefore - 5000L, fromWallet.getBalance());
        assertEquals(smallerBalanceBefore + 5000L, toWallet.getBalance());
    }

    @Test
    void executeTransferMarksFailedAndKeepsRowWhenBalanceInsufficient() {
        // given: fromWallet 잔액(100000)보다 훨씬 큰 금액으로 송금 시도
        TransferVO pending = transferService.createPendingTransfer(fromWalletId, toWalletId, 999_999_999L, TransferType.TRANSFER, newIdempotencyKey());

        // when & then: executeTransfer() 자체는 예외를 그대로 다시 던진다
        BusinessException exception = assertThrows(BusinessException.class, () -> transferService.executeTransfer(pending.getId()));
        assertEquals(WalletErrorCode.INSUFFICIENT_BALANCE, exception.getErrorCode());
        System.out.println("[EXCEPTION] " + exception.getErrorCode());

        // then: 실패했어도 T_WLT_TRF_L 행 자체는 살아있고, 상태만 FAILED로 바뀌어야 한다.
        // 이게 2단계로 트랜잭션을 나눈 진짜 이유 - createPendingTransfer()는 이미 독립적으로
        // 커밋됐기 때문에, executeTransfer() 안에서의 실패가 이 행까지 지우지 않는다.
        TransferVO stored = transferMapper.selectByIdempotencyKey(pending.getIdempotencyKey());
        System.out.println("[VERIFY] 실패 후에도 남은 행: id=" + stored.getId()
                + ", status=" + stored.getStatus() + ", failureReason=" + stored.getFailureReason());

        assertNotNull(stored);
        assertEquals("FAILED", stored.getStatus());
        assertEquals("INSUFFICIENT_BALANCE", stored.getFailureReason());
    }

    @Test
    void executeTransferDoesNotReprocessWhenAlreadyCompleted() {
        // given: 정상적으로 한번 완료된 송금
        TransferVO pending = transferService.createPendingTransfer(fromWalletId, toWalletId, 10000L, TransferType.TRANSFER, newIdempotencyKey());
        TransferVO completed = transferService.executeTransfer(pending.getId());
        assertEquals("COMPLETED", completed.getStatus());

        WalletVO fromAfterFirst = walletMapper.selectWalletForUpdate(fromWalletId);
        WalletVO toAfterFirst = walletMapper.selectWalletForUpdate(toWalletId);
        System.out.println("[FIRST] from=" + fromAfterFirst.getBalance() + ", to=" + toAfterFirst.getBalance());

        // when: 이미 COMPLETED된 같은 TransferVO로 executeTransfer()를 또 호출
        TransferVO result = transferService.executeTransfer(completed.getId());
        System.out.println("[SECOND CALL] status=" + result.getStatus());

        // then: 상태는 그대로 COMPLETED, 잔액도 한 번 더 안 바뀌어야 한다 (재처리 안 됨)
        assertEquals("COMPLETED", result.getStatus());

        WalletVO fromAfterSecond = walletMapper.selectWalletForUpdate(fromWalletId);
        WalletVO toAfterSecond = walletMapper.selectWalletForUpdate(toWalletId);
        System.out.println("[SECOND] from=" + fromAfterSecond.getBalance() + ", to=" + toAfterSecond.getBalance());

        assertEquals(fromAfterFirst.getBalance(), fromAfterSecond.getBalance());
        assertEquals(toAfterFirst.getBalance(), toAfterSecond.getBalance());

        // TransferType.TRANSFER는 용돈이 아니라서, 재시도든 아니든 알림은 아예 안 가야 한다.
        verifyNoInteractions(notificationService);
    }

    // 용돈(ALLOWANCE) 송금이 실제로 완료됐을 때, 받는 사람(toWalletId 주인)한테 알림이
    // 정확히 한 번만 가는지 확인한다. "한 번만"이 중요한 이유: 같은 송금을 재시도해도
    // (idempotencyKey 재사용) 돈이 중복으로 안 옮겨지듯이, 알림도 중복으로 가면 안 된다.
    @Test
    void executeTransferNotifiesRecipientOnceForAllowanceAndNotAgainOnRetry() {
        // toWalletId는 setUp()에서 memberId=2L로 만들어둔 지갑이라, 알림 수신자는 2L이어야 한다.
        TransferVO pending = transferService.createPendingTransfer(
                fromWalletId, toWalletId, 10000L, TransferType.ALLOWANCE, newIdempotencyKey());

        TransferVO first = transferService.executeTransfer(pending.getId());
        assertEquals("COMPLETED", first.getStatus());

        // 같은 송금 id로 재시도 - lockAndMove()가 이미 COMPLETED라 재처리는 안 하지만,
        // executeTransfer() 자체는 다시 호출된다(예: 클라이언트가 같은 요청을 재시도하는 상황).
        TransferVO second = transferService.executeTransfer(first.getId());
        assertEquals("COMPLETED", second.getStatus());

        // 알림은 딱 한 번만 갔어야 한다 (재시도로 두 번 가면 안 됨)
        verify(notificationService, times(1)).createNotification(
                eq(2L), eq("용돈이 입금 됐어요"), eq("10,000원"),
                eq(NotificationReferenceType.TRANSFER), eq((Long) null), eq(true));
    }

}
