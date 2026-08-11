package com.teenyfin.teenymoney.domain.wallet.service;

import com.teenyfin.teenymoney.domain.wallet.mapper.TransferMapper;
import com.teenyfin.teenymoney.domain.wallet.vo.TransferType;
import com.teenyfin.teenymoney.domain.wallet.vo.TransferVO;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("호출자 트랜잭션 종속 송금")
class TransferServiceAtomicTest {

    private TransferMapper transferMapper;
    private TransferExecutor transferExecutor;
    private TransferService transferService;

    @BeforeEach
    void setUp() {
        transferMapper = mock(TransferMapper.class);
        transferExecutor = mock(TransferExecutor.class);
        transferService = new TransferService(
                transferMapper,
                transferExecutor,
                mock(TransferFailureRecorder.class));
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
    }
}
