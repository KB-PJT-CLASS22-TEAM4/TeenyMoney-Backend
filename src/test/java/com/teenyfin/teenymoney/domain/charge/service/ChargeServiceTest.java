package com.teenyfin.teenymoney.domain.charge.service;

import com.teenyfin.teenymoney.config.LazyBeanInitializer;
import com.teenyfin.teenymoney.config.RootConfig;
import com.teenyfin.teenymoney.domain.charge.crypto.BillingKeyEncryptor;
import com.teenyfin.teenymoney.domain.charge.exception.ChargeErrorCode;
import com.teenyfin.teenymoney.domain.charge.mapper.ChargeMapper;
import com.teenyfin.teenymoney.domain.charge.mapper.ChargeMethodMapper;
import com.teenyfin.teenymoney.domain.charge.toss.TossPaymentsClient;
import com.teenyfin.teenymoney.domain.charge.toss.dto.TossBillingApproveRequestDTO;
import com.teenyfin.teenymoney.domain.charge.toss.dto.TossPaymentApprovalResponseDTO;
import com.teenyfin.teenymoney.domain.charge.vo.ChargeVO;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.service.WalletLedgerService;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
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
import org.springframework.web.client.ResourceAccessException;

import javax.sql.DataSource;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// 클래스에 @Transactional을 일부러 안 붙인다 - TransferServiceTest와 같은 이유.
// executeCharge()가 NOT_SUPPORTED라서, 진짜로 커밋되는지/실패해도 행이 안 사라지는지를
// 검증하려면 각 단계가 실제로 커밋돼야 한다. 대신 @AfterEach에서 직접 SQL로 정리한다.
@ExtendWith(SpringExtension.class)
// LazyBeanInitializer가 필요한 이유: RootConfig가 도메인 전체를 스캔하다가 TossPaymentsClient
// (RestTemplate 빈 필요 - 근데 RestTemplateConfig는 이 좁은 테스트 컨텍스트엔 없음)까지
// 즉시 만들려다 실패하는 걸 막아준다. ChargeMethodServiceTest와 같은 이유.
@ContextConfiguration(classes = RootConfig.class, initializers = LazyBeanInitializer.class)
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
public class ChargeServiceTest {

    @Autowired
    private ChargeMapper chargeMapper;

    @Autowired
    private ChargeMethodMapper chargeMethodMapper;

    @Autowired
    private WalletMapper walletMapper;

    @Autowired
    private MemberMapper memberMapper;

    private JdbcTemplate jdbcTemplate;

    // 진짜 토스 서버를 호출하면 안 되니 Mockito로 가짜 응답/예외를 만들어 넣는다.
    private TossPaymentsClient tossPaymentsClient;

    // 암호화는 외부 의존이 없는 순수 로직이라 mock 안 하고 실제로 사용한다 (ChargeMethodServiceTest와 같은 이유).
    private BillingKeyEncryptor billingKeyEncryptor;

    // ChargeService/ChargeExecutor/ChargeFailureRecorder는 @Service라서 이 root-only
    // 컨텍스트엔 빈으로 없다. 진짜 매퍼를 직접 new로 꽂아서 만든다 (TransferServiceTest와 같은 방식).
    private ChargeService chargeService;

    private Long parentId;
    private Long walletId;
    private Long paymentMethodId;

    // 등록할 때 암호화하기 전 원본 빌링키 값.
    private static final String PLAIN_BILLING_KEY = "test-real-billing-key";

    @Autowired
    void setDataSource(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void setUp() {
        tossPaymentsClient = mock(TossPaymentsClient.class);

        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        billingKeyEncryptor = new BillingKeyEncryptor(Base64.getEncoder().encodeToString(keyBytes));

        WalletLedgerService walletLedgerService = new WalletLedgerService(walletMapper);
        ChargeExecutor chargeExecutor = new ChargeExecutor(chargeMapper, walletLedgerService);
        ChargeFailureRecorder chargeFailureRecorder = new ChargeFailureRecorder(chargeMapper);

        chargeService = new ChargeService(
                chargeMapper, chargeMethodMapper, walletMapper, memberMapper,
                tossPaymentsClient, billingKeyEncryptor, chargeExecutor, chargeFailureRecorder);

        parentId = insertMember("PARENT");
        walletId = insertWallet(parentId, 100000L);
        // 결제수단은 반드시 "진짜로 암호화된" billing_key를 넣어야 한다 - executeCharge()가
        // billingKeyEncryptor.decrypt()를 실제로 부르기 때문에, 가짜 텍스트를 넣으면
        // Postman에서 봤던 것과 똑같은 Base64 에러가 테스트에서도 그대로 재현된다.
        paymentMethodId = insertPaymentMethod(parentId, billingKeyEncryptor.encrypt(PLAIN_BILLING_KEY), "ACTIVE");

        System.out.println("[SETUP] parentId=" + parentId + ", walletId=" + walletId
                + "(100000원), paymentMethodId=" + paymentMethodId);
    }

    @AfterEach
    void tearDown() {
        // FK(ON DELETE RESTRICT) 때문에 자식 행부터 지워야 한다.
        jdbcTemplate.update("DELETE FROM T_WLT_HIST_H WHERE wallet_id = ?", walletId);
        jdbcTemplate.update("DELETE FROM T_WLT_CHARGE_L WHERE wallet_id = ?", walletId);
        jdbcTemplate.update("DELETE FROM T_PAY_METHOD_M WHERE parent_id = ?", parentId);
        jdbcTemplate.update("DELETE FROM T_WLT_BASE_M WHERE id = ?", walletId);
        jdbcTemplate.update("DELETE FROM T_MBR_INFO_M WHERE id = ?", parentId);
    }

    // ---------- 헬퍼 ----------

    private MemberPrincipal principal() {
        return new MemberPrincipal(parentId, "PARENT");
    }

    private String newIdempotencyKey() {
        return UUID.randomUUID().toString();
    }

    // TossPaymentsClient.approveBillingPayment(...)가 이렇게 응답한다고 가짜로 정해두는 헬퍼.
    private void stubTossApprovalSuccess(String paymentKey) {
        TossPaymentApprovalResponseDTO response = new TossPaymentApprovalResponseDTO();
        response.setPaymentKey(paymentKey);
        response.setStatus("DONE");
        when(tossPaymentsClient.approveBillingPayment(
                anyString(), any(TossBillingApproveRequestDTO.class), anyString()))
                .thenReturn(response);
    }

    // 토스가 승인 자체를 거절하는 상황(카드 거절, 한도초과 등)을 흉내낸다.
    private void stubTossApprovalRejected() {
        when(tossPaymentsClient.approveBillingPayment(
                anyString(), any(TossBillingApproveRequestDTO.class), anyString()))
                .thenThrow(new BusinessException(ChargeErrorCode.TOSS_CHARGE_APPROVAL_FAILED));
    }

    // 토스가 "아직 처리 중"이라고 응답하는 상황(같은 멱등키로 첫 요청이 아직 안 끝남)을 흉내낸다.
    private void stubTossRequestInProgress() {
        when(tossPaymentsClient.approveBillingPayment(
                anyString(), any(TossBillingApproveRequestDTO.class), anyString()))
                .thenThrow(new BusinessException(ChargeErrorCode.TOSS_REQUEST_IN_PROGRESS));
    }

    // 통신 자체가 끊기는 상황(타임아웃, 연결 거부 등)을 흉내낸다.
    // ResourceAccessException은 RestClientException의 하위 타입 - approveBillingPayment()의
    // catch(HttpStatusCodeException)에 안 걸리고 그대로 ChargeService까지 전파되는 케이스.
    private void stubTossCommunicationFailure() {
        when(tossPaymentsClient.approveBillingPayment(
                anyString(), any(TossBillingApproveRequestDTO.class), anyString()))
                .thenThrow(new ResourceAccessException("커넥션 타임아웃"));
    }

    private Long insertMember(String role) {
        String unique = UUID.randomUUID().toString().replace("-", "");
        int phoneSuffix = Math.floorMod(unique.hashCode(), 100_000_000);

        MemberVO member = new MemberVO();
        member.setRole(role);
        member.setName("충전테스트");
        member.setBirthDate(LocalDate.of(1990, 1, 1));
        member.setPhoneNumber(String.format("010%08d", phoneSuffix));
        member.setEmail("charge-service-" + unique + "@test.local");
        member.setPassword("$2a$10$test-only-hash");
        memberMapper.insert(member);
        return member.getId();
    }

    private Long insertWallet(Long memberId, Long balance) {
        jdbcTemplate.update(
                "INSERT INTO T_WLT_BASE_M (member_id, balance, type) VALUES (?, ?, 'MEMBER')",
                memberId, balance);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private Long insertPaymentMethod(Long parentId, String encryptedBillingKey, String status) {
        jdbcTemplate.update(
                "INSERT INTO T_PAY_METHOD_M (parent_id, billing_key, type, card_company, masked_card_number, status) " +
                        "VALUES (?, ?, 'CARD', '테스트카드사', '1234-56**-****-7890', ?)",
                parentId, encryptedBillingKey, status);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    // ---------- createPendingCharge() ----------

    @Test
    void createPendingChargeInsertsRowWithPendingStatus() {
        String idempotencyKey = newIdempotencyKey();

        ChargeVO pending = chargeService.createPendingCharge(principal(), paymentMethodId, 10000L, idempotencyKey);
        System.out.println("[CREATE] chargeId=" + pending.getId() + ", status=" + pending.getStatus()
                + ", orderId=" + pending.getOrderId());

        assertNotNull(pending.getId());
        assertEquals("PENDING", pending.getStatus());
        assertNotNull(pending.getOrderId());

        ChargeVO stored = chargeMapper.selectByIdempotencyKey(idempotencyKey);
        assertNotNull(stored);
        assertEquals(pending.getId(), stored.getId());
    }

    @Test
    void createPendingChargeReturnsSameRowWhenIdempotencyKeyReused() {
        String idempotencyKey = newIdempotencyKey();

        ChargeVO first = chargeService.createPendingCharge(principal(), paymentMethodId, 10000L, idempotencyKey);
        ChargeVO second = chargeService.createPendingCharge(principal(), paymentMethodId, 10000L, idempotencyKey);

        System.out.println("[IDEMPOTENCY] first.id=" + first.getId() + ", second.id=" + second.getId());
        assertEquals(first.getId(), second.getId());
    }

    @Test
    void createPendingChargeThrowsWhenIdempotencyKeyReusedWithDifferentAmount() {
        String idempotencyKey = newIdempotencyKey();
        chargeService.createPendingCharge(principal(), paymentMethodId, 10000L, idempotencyKey);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> chargeService.createPendingCharge(principal(), paymentMethodId, 20000L, idempotencyKey));

        System.out.println("[EXCEPTION] " + exception.getErrorCode());
        assertEquals(ChargeErrorCode.IDEMPOTENCY_KEY_CONFLICT, exception.getErrorCode());
    }

    @Test
    void createPendingChargeThrowsWhenPaymentMethodNotFound() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> chargeService.createPendingCharge(principal(), 999_999_999L, 10000L, newIdempotencyKey()));

        System.out.println("[EXCEPTION] " + exception.getErrorCode());
        assertEquals(ChargeErrorCode.CHARGE_METHOD_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void createPendingChargeThrowsWhenPaymentMethodInactive() {
        Long inactiveId = insertPaymentMethod(parentId, billingKeyEncryptor.encrypt("inactive-key"), "INACTIVE");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> chargeService.createPendingCharge(principal(), inactiveId, 10000L, newIdempotencyKey()));

        System.out.println("[EXCEPTION] " + exception.getErrorCode());
        assertEquals(ChargeErrorCode.CHARGE_METHOD_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void createPendingChargeThrowsWhenPaymentMethodBelongsToAnotherParent() {
        // given: 다른 부모와 그 부모 소유의 결제수단을 하나 더 만든다
        Long otherParentId = insertMember("PARENT");
        Long otherPaymentMethodId = insertPaymentMethod(
                otherParentId, billingKeyEncryptor.encrypt("other-parent-key"), "ACTIVE");

        try {
            // when & then: 내 principal로 남의 결제수단 id를 써서 충전 시도
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> chargeService.createPendingCharge(principal(), otherPaymentMethodId, 10000L, newIdempotencyKey()));

            System.out.println("[EXCEPTION] " + exception.getErrorCode());
            assertEquals(ChargeErrorCode.CHARGE_METHOD_ACCESS_DENIED, exception.getErrorCode());
        } finally {
            // 이 테스트 안에서만 만든 데이터라 여기서 직접 정리한다 (다른 테스트의 tearDown과 안 겹치게)
            jdbcTemplate.update("DELETE FROM T_PAY_METHOD_M WHERE id = ?", otherPaymentMethodId);
            jdbcTemplate.update("DELETE FROM T_MBR_INFO_M WHERE id = ?", otherParentId);
        }
    }

    // ---------- executeCharge() ----------

    @Test
    void executeChargeCreditsWalletAndMarksSuccessWhenTossApproves() {
        stubTossApprovalSuccess("toss-payment-key-abc");

        Long balanceBefore = walletMapper.selectWalletForUpdate(walletId).getBalance();
        System.out.println("[BEFORE] balance=" + balanceBefore);

        ChargeVO pending = chargeService.createPendingCharge(principal(), paymentMethodId, 10000L, newIdempotencyKey());
        ChargeVO result = chargeService.executeCharge(pending.getId());

        System.out.println("[EXECUTE] status=" + result.getStatus() + ", paymentKey=" + result.getPaymentKey());
        assertEquals("SUCCESS", result.getStatus());
        assertEquals("toss-payment-key-abc", result.getPaymentKey());

        WalletVO walletAfter = walletMapper.selectWalletForUpdate(walletId);
        System.out.println("[AFTER] balance=" + walletAfter.getBalance());
        assertEquals(balanceBefore + 10000L, walletAfter.getBalance());
    }

    @Test
    void executeChargeDoesNotReprocessWhenAlreadySuccess() {
        stubTossApprovalSuccess("toss-payment-key-once");

        ChargeVO pending = chargeService.createPendingCharge(principal(), paymentMethodId, 10000L, newIdempotencyKey());
        ChargeVO first = chargeService.executeCharge(pending.getId());
        assertEquals("SUCCESS", first.getStatus());

        Long balanceAfterFirst = walletMapper.selectWalletForUpdate(walletId).getBalance();
        System.out.println("[FIRST] balance=" + balanceAfterFirst);

        // when: 이미 SUCCESS인 같은 chargeId로 executeCharge()를 또 호출
        ChargeVO second = chargeService.executeCharge(pending.getId());
        System.out.println("[SECOND CALL] status=" + second.getStatus());

        // then: 잔액이 한 번 더 안 올라가야 한다 (재처리 안 됨)
        Long balanceAfterSecond = walletMapper.selectWalletForUpdate(walletId).getBalance();
        System.out.println("[SECOND] balance=" + balanceAfterSecond);
        assertEquals("SUCCESS", second.getStatus());
        assertEquals(balanceAfterFirst, balanceAfterSecond);
    }

    @Test
    void executeChargeMarksFailedAndKeepsWalletUnchangedWhenTossRejects() {
        stubTossApprovalRejected();

        Long balanceBefore = walletMapper.selectWalletForUpdate(walletId).getBalance();

        ChargeVO pending = chargeService.createPendingCharge(principal(), paymentMethodId, 10000L, newIdempotencyKey());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> chargeService.executeCharge(pending.getId()));
        System.out.println("[EXCEPTION] " + exception.getErrorCode());
        assertEquals(ChargeErrorCode.TOSS_CHARGE_APPROVAL_FAILED, exception.getErrorCode());

        // then: 행은 안 사라지고 FAILED로 남아야 하고, 실패 사유가 기록돼야 한다
        ChargeVO stored = chargeMapper.selectById(pending.getId());
        System.out.println("[VERIFY] status=" + stored.getStatus() + ", failureReason=" + stored.getFailureReason());
        assertEquals("FAILED", stored.getStatus());
        assertEquals("TOSS_CHARGE_APPROVAL_FAILED", stored.getFailureReason());

        // then: 지갑 잔액은 절대 바뀌면 안 된다
        Long balanceAfter = walletMapper.selectWalletForUpdate(walletId).getBalance();
        System.out.println("[VERIFY] balanceBefore=" + balanceBefore + ", balanceAfter=" + balanceAfter);
        assertEquals(balanceBefore, balanceAfter);
    }

    @Test
    void executeChargeRevertsToPendingWhenTossStillProcessing() {
        stubTossRequestInProgress();

        ChargeVO pending = chargeService.createPendingCharge(principal(), paymentMethodId, 10000L, newIdempotencyKey());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> chargeService.executeCharge(pending.getId()));
        System.out.println("[EXCEPTION] " + exception.getErrorCode());
        assertEquals(ChargeErrorCode.TOSS_REQUEST_IN_PROGRESS, exception.getErrorCode());

        // then: FAILED가 아니라 PENDING으로 되돌아가야 한다 - 나중에 같은 orderId+
        // Idempotency-Key로 재시도할 수 있어야 하기 때문 (executeCharge() 설계의 핵심).
        ChargeVO stored = chargeMapper.selectById(pending.getId());
        System.out.println("[VERIFY] status=" + stored.getStatus() + " (PENDING이어야 재시도 가능)");
        assertEquals("PENDING", stored.getStatus());
    }

    @Test
    void executeChargeRevertsToPendingOnCommunicationFailure() {
        stubTossCommunicationFailure();

        ChargeVO pending = chargeService.createPendingCharge(principal(), paymentMethodId, 10000L, newIdempotencyKey());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> chargeService.executeCharge(pending.getId()));
        System.out.println("[EXCEPTION] " + exception.getErrorCode());
        assertEquals(ChargeErrorCode.TOSS_REQUEST_IN_PROGRESS, exception.getErrorCode());

        ChargeVO stored = chargeMapper.selectById(pending.getId());
        System.out.println("[VERIFY] status=" + stored.getStatus() + " (PENDING이어야 재시도 가능)");
        assertEquals("PENDING", stored.getStatus());
    }

    @Test
    void executeChargeSucceedsOnRetryAfterCommunicationFailure() {
        // given: 첫 시도는 통신 실패로 PENDING으로 되돌아감
        stubTossCommunicationFailure();
        ChargeVO pending = chargeService.createPendingCharge(principal(), paymentMethodId, 10000L, newIdempotencyKey());
        assertThrows(BusinessException.class, () -> chargeService.executeCharge(pending.getId()));

        ChargeVO afterFailure = chargeMapper.selectById(pending.getId());
        System.out.println("[AFTER FAILURE] status=" + afterFailure.getStatus());
        assertEquals("PENDING", afterFailure.getStatus());

        // when: 같은 chargeId로 재시도했을 때 이번엔 토스가 성공 응답을 준다고 가정
        stubTossApprovalSuccess("toss-payment-key-retry");
        ChargeVO result = chargeService.executeCharge(pending.getId());

        // then: 재시도가 실제로 다시 선점하고 성공까지 완료돼야 한다
        // (claimForProcessing()이 PENDING 상태를 보고 다시 통과시켜야 가능한 부분)
        System.out.println("[RETRY RESULT] status=" + result.getStatus());
        assertEquals("SUCCESS", result.getStatus());

        WalletVO walletAfter = walletMapper.selectWalletForUpdate(walletId);
        System.out.println("[AFTER RETRY] balance=" + walletAfter.getBalance());
        assertEquals(110000L, walletAfter.getBalance());
    }

    @Test
    void executeChargeThrowsWhenChargeNotFound() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> chargeService.executeCharge(999_999_999L));

        System.out.println("[EXCEPTION] " + exception.getErrorCode());
        assertEquals(ChargeErrorCode.CHARGE_NOT_FOUND, exception.getErrorCode());
    }
}
