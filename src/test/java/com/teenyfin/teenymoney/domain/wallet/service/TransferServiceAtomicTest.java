package com.teenyfin.teenymoney.domain.wallet.service;

import com.teenyfin.teenymoney.domain.notification.service.NotificationService;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;
import com.teenyfin.teenymoney.domain.wallet.mapper.TransferMapper;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.vo.TransferType;
import com.teenyfin.teenymoney.domain.wallet.vo.TransferVO;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import com.teenyfin.teenymoney.global.sse.SseEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("호출자 트랜잭션 종속 송금")
class TransferServiceAtomicTest {

    private TransferMapper transferMapper;
    private TransferExecutor transferExecutor;
    private TransferService transferService;

    // 이 클래스가 테스트하는 transferInExistingTransaction()은 TransferService.executeTransfer()가
    // 아니라 다른 메서드다 - 알림은 executeTransfer()에서만 나가므로, 여기선 알림이 한 번도
    // 불리면 안 된다(아래 verifyNoInteractions 참고). 그래도 생성자엔 넘겨야 컴파일된다.
    private NotificationService notificationService;

    private WalletMapper walletMapper;
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        transferMapper = mock(TransferMapper.class);
        transferExecutor = mock(TransferExecutor.class);
        notificationService = mock(NotificationService.class);
        walletMapper = mock(WalletMapper.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        transferService = new TransferService(
                transferMapper,
                transferExecutor,
                mock(TransferFailureRecorder.class),
                walletMapper,
                notificationService,
                eventPublisher);
    }

    private WalletVO wallet(Long walletId, Long memberId) {
        WalletVO wallet = new WalletVO();
        wallet.setId(walletId);
        wallet.setMemberId(memberId);
        return wallet;
    }

    @Test
    @DisplayName("원자 송금 메서드는 기존 트랜잭션이 없으면 실행할 수 없도록 MANDATORY다")
    void atomicTransferRequiresExistingTransaction() throws Exception {
        Method method = TransferService.class.getMethod(
                "transferInExistingTransaction",
                Long.class,
                Long.class,
                Long.class,
                TransferType.class,
                String.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertEquals(Propagation.MANDATORY, transactional.propagation());
    }

    @Test
    @DisplayName("원자 송금은 PENDING 행 생성과 실제 잔액 이동을 한 호출에서 이어서 수행한다")
    void atomicTransferCreatesAndExecutesTransfer() {
        doAnswer(invocation -> {
            TransferVO transfer = invocation.getArgument(0);
            transfer.setId(77L);
            return null;
        }).when(transferMapper).insertTransfer(any(TransferVO.class));

        TransferVO completed = new TransferVO();
        completed.setId(77L);
        completed.setStatus("COMPLETED");
        when(transferExecutor.lockAndMove(77L)).thenReturn(completed);

        TransferVO result = transferService.transferInExistingTransaction(
                10L,
                20L,
                3_000L,
                TransferType.QUEST_REWARD,
                "QUEST_REWARD:104");

        assertSame(completed, result);
        verify(transferMapper).insertTransfer(any(TransferVO.class));
        verify(transferExecutor).lockAndMove(77L);
        // 알림은 executeTransfer()에서만 나간다 - 이 메서드(transferInExistingTransaction)는
        // 건드리지 않으므로 한 번도 불리면 안 된다.
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("같은 멱등성 키의 송금이 있으면 새 행 없이 기존 송금만 안전하게 재확인한다")
    void atomicTransferReusesExistingIdempotentTransfer() {
        TransferVO existing = new TransferVO();
        existing.setId(88L);
        existing.setFromWalletId(10L);
        existing.setToWalletId(20L);
        existing.setAmount(3_000L);
        existing.setType(TransferType.QUEST_REWARD.name());
        existing.setStatus("COMPLETED");
        when(transferMapper.selectByIdempotencyKey("QUEST_REWARD:104"))
                .thenReturn(existing);
        when(transferExecutor.lockAndMove(88L)).thenReturn(existing);

        TransferVO result = transferService.transferInExistingTransaction(
                10L,
                20L,
                3_000L,
                TransferType.QUEST_REWARD,
                "QUEST_REWARD:104");

        assertSame(existing, result);
        verify(transferMapper, never()).insertTransfer(any());
        verify(transferExecutor).lockAndMove(88L);
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("송금이 끝나면 받는 자녀뿐 아니라 보내는 부모의 화면도 갱신시킨다")
    void 송금은_양쪽_지갑_주인의_화면을_갱신시킨다() {
        Long parentId = 1L;
        Long childId = 2L;

        TransferVO completed = new TransferVO();
        completed.setId(55L);
        completed.setFromWalletId(10L);
        completed.setToWalletId(20L);
        completed.setAmount(30_000L);
        completed.setType(TransferType.ALLOWANCE.name());
        completed.setStatus("COMPLETED");

        when(transferMapper.selectById(55L)).thenReturn(null);
        when(transferExecutor.lockAndMove(55L)).thenReturn(completed);
        when(walletMapper.selectById(10L)).thenReturn(wallet(10L, parentId));
        when(walletMapper.selectById(20L)).thenReturn(wallet(20L, childId));

        transferService.executeTransfer(55L);

        // 자녀는 알림으로도 알게 되지만, 부모는 알림을 받지 않는다.
        // 정기 용돈은 새벽에 자동으로 실행되므로 부모에겐 응답으로 갱신될 기회조차 없다.
        verify(eventPublisher).publishEvent(
                new SseEvent(parentId, NotificationReferenceType.TRANSFER));
        verify(eventPublisher).publishEvent(
                new SseEvent(childId, NotificationReferenceType.TRANSFER));
    }

    @Test
    @DisplayName("이미 완료된 송금을 재확인만 한 경우엔 화면을 갱신시키지 않는다")
    void 재처리가_아니면_신호를_보내지_않는다() {
        TransferVO completed = new TransferVO();
        completed.setId(55L);
        completed.setFromWalletId(10L);
        completed.setToWalletId(20L);
        completed.setType(TransferType.ALLOWANCE.name());
        completed.setStatus("COMPLETED");

        // 같은 idempotencyKey로 재시도가 들어온 상황. 잔액은 이번 호출로 움직이지 않았다.
        when(transferMapper.selectById(55L)).thenReturn(completed);
        when(transferExecutor.lockAndMove(55L)).thenReturn(completed);

        transferService.executeTransfer(55L);

        verifyNoInteractions(eventPublisher);
        verifyNoInteractions(notificationService);
    }
}
