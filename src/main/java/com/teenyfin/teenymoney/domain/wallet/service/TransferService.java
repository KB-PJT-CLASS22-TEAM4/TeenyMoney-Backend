package com.teenyfin.teenymoney.domain.wallet.service;

import com.teenyfin.teenymoney.domain.wallet.exception.WalletErrorCode;
import com.teenyfin.teenymoney.domain.wallet.mapper.TransferMapper;
import com.teenyfin.teenymoney.domain.wallet.vo.TransferType;
import com.teenyfin.teenymoney.domain.wallet.vo.TransferVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import com.teenyfin.teenymoney.global.exception.CommonErrorCode;
import com.teenyfin.teenymoney.domain.notification.service.NotificationService;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import lombok.extern.slf4j.Slf4j;


// 레이어2: T_WLT_TRF_L(송금 시도 기록) 전담. 실제 잔액 이동(잠금+debit/credit)은 TransferExecutor에,
// 실패 기록은 TransferFailureRecorder에 위임하고, 이 클래스는 그 둘을 오케스트레이션만 한다.
@Service
@Slf4j
public class TransferService {

    // 자녀가 용돈을 받았을 때 보내는 알림 문구. "알림 딥링크 인벤토리" 문서의
    // (TRANSFER, 자녀) 행 그대로 - referenceId는 항상 null로 보낸다(아래 참고).
    private static final String ALLOWANCE_RECEIVED_TITLE = "용돈이 입금됐어요";

    private final TransferMapper transferMapper;
    private final TransferExecutor transferExecutor;
    private final TransferFailureRecorder transferFailureRecorder;
    private final WalletMapper walletMapper;
    private final NotificationService notificationService;

    public TransferService(TransferMapper transferMapper, TransferExecutor transferExecutor, TransferFailureRecorder transferFailureRecorder, WalletMapper walletMapper, NotificationService notificationService) {
        this.transferMapper = transferMapper;
        this.transferExecutor = transferExecutor;
        this.transferFailureRecorder = transferFailureRecorder;
        this.walletMapper = walletMapper;
        this.notificationService = notificationService;
    }

    // 같은 idempotencyKey로 이미 존재하는 행(existing)이, 지금 들어온 요청의 내용과
    // 실제로 같은지 확인한다. 다르면 "이 키는 이미 다른 내용으로 쓰였다"는 뜻이므로 예외를 던진다.
    private void ensureMatchesRequestOrThrow(
            TransferVO existing, Long fromWalletId, Long toWalletId, Long amount, TransferType type) {
        if (!existing.getFromWalletId().equals(fromWalletId)
                || !existing.getToWalletId().equals(toWalletId)
                || !existing.getAmount().equals(amount)
                || !existing.getType().equals(type.name())) {
            throw new BusinessException(WalletErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
    }

    // 1단계: 송금을 "PENDING" 상태로 접수만 해둔다. 잔액은 아직 안 건드린다.
    @Transactional
    public TransferVO createPendingTransfer(Long fromWalletId, Long toWalletId, Long amount, TransferType type, String idempotencyKey) {
        return createOrLoadPendingTransfer(
                fromWalletId, toWalletId, amount, type, idempotencyKey);
    }

    /**
     * 호출한 서비스의 트랜잭션 안에서 송금 접수와 잔액 이동을 함께 처리한다.
     * 퀘스트 승인 같은 상위 업무가 뒤에서 실패하면 송금과 원장도 같이 롤백된다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public TransferVO transferInExistingTransaction(
            Long fromWalletId,
            Long toWalletId,
            Long amount,
            TransferType type,
            String idempotencyKey) {
        TransferVO pending = createOrLoadPendingTransfer(
                fromWalletId, toWalletId, amount, type, idempotencyKey);
        return transferExecutor.lockAndMove(pending.getId());
    }

    private TransferVO createOrLoadPendingTransfer(
            Long fromWalletId,
            Long toWalletId,
            Long amount,
            TransferType type,
            String idempotencyKey) {
        if (amount == null || amount <= 0) {
            throw new BusinessException(WalletErrorCode.INVALID_TRANSFER_AMOUNT);
        }

        if (fromWalletId == null || toWalletId == null) {
            throw new BusinessException(WalletErrorCode.INVALID_WALLET_ID);
        }

        // type이 null이면 바로 아래 type.name() 호출에서 NullPointerException이 난다.
        // 다른 필수값들과 같은 이유로, 여기서 명확한 400으로 먼저 막는다.
        if (type == null) {
            throw new BusinessException(WalletErrorCode.INVALID_TRANSFER_TYPE);
        }

        // idempotencyKey가 null이면 selectByIdempotencyKey(null)이 아무것도 못 찾아서
        // 그대로 통과되고, insertTransfer()에서 idempotency_key NOT NULL 제약에 걸려
        // 원시 SQL 예외로 나가버린다. 마찬가지로 여기서 미리 막는다.
        if (idempotencyKey == null) {
            throw new BusinessException(WalletErrorCode.INVALID_IDEMPOTENCY_KEY);
        }

        if (fromWalletId.equals(toWalletId)) {
            throw new BusinessException(WalletErrorCode.TRANSFER_SAME_WALLET);
        }

        TransferVO existing = transferMapper.selectByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            ensureMatchesRequestOrThrow(existing, fromWalletId, toWalletId, amount, type);
            return existing;
        }

        TransferVO transfer = new TransferVO();
        transfer.setFromWalletId(fromWalletId);
        transfer.setToWalletId(toWalletId);
        transfer.setAmount(amount);
        transfer.setType(type.name());
        transfer.setIdempotencyKey(idempotencyKey);

        try {
            transferMapper.insertTransfer(transfer);
        } catch (DuplicateKeyException e) {
            TransferVO winner = transferMapper.selectByIdempotencyKey(idempotencyKey);
            if (winner == null) {
                // UNIQUE 제약 위반은 났는데(=이 키를 가진 행이 존재한다는 뜻) 방금 이 SELECT엔
                // 아직 안 보이는 경우 - 경쟁 트랜잭션이 아직 커밋 전이라 격리수준 때문에 못 볼
                // 수 있다. 실제로 존재는 하므로, "이 키는 이미 사용 중"이라는 뜻으로 던진다.
                throw new BusinessException(WalletErrorCode.IDEMPOTENCY_KEY_CONFLICT);
            }
            ensureMatchesRequestOrThrow(winner, fromWalletId, toWalletId, amount, type);

            return winner;
        } catch (DataIntegrityViolationException e) {
            // idempotency_key UNIQUE 위반(DuplicateKeyException)이 아닌 다른 무결성 제약 위반이면,
            // 여기까지 오는 값 중 amount/type은 이미 위에서 다 검증했으니 남은 유력한 원인은
            // from/toWalletId가 실제로 존재하지 않는 지갑이라 FK_T_WLT_BASE_M_TO_T_WLT_TRF_L_1/_2에
            // 걸린 경우다. DuplicateKeyException은 DataIntegrityViolationException의 하위 타입이라
            // catch 순서가 중요하다 - 반드시 더 구체적인 DuplicateKeyException을 먼저 잡아야 한다.
            throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }

        transfer.setStatus("PENDING");
        return transfer;
    }

    // 2단계: 실제로 잔액을 옮긴다. 잠금+이동은 TransferExecutor(별도 빈)에게 통째로 맡긴다.
    // 이 메서드 자체는 절대 @Transactional을 붙이면 안 된다 - 붙이면 transferExecutor.lockAndMove()
    // 호출과 markFailed() 호출이 다시 같은 하나의 트랜잭션 흐름 안에 놓이면서, 롤백이 안 끝난
    // 채로 markFailed()가 실행돼 예전의 데드락 문제가 그대로 재현된다. 이 메서드가 트랜잭션이
    // 없어야, transferExecutor.lockAndMove()의 트랜잭션이 "완전히 끝난 뒤에"(성공이든 실패든)
    // 아래 코드가 실행된다는 게 보장된다.

    // NOT_SUPPORTED: "이 메서드는 절대 트랜잭션 안에서 실행되면 안 된다"를 코드로 강제한다.
    // 나중에 누군가 이 메서드(혹은 이걸 부르는 상위 메서드)에 실수로 @Transactional을 붙이더라도,
    // 스프링이 그 바깥 트랜잭션을 이 메서드가 실행되는 동안 잠깐 미뤄두기 때문에,
    // transferExecutor.lockAndMove()의 락과 markFailed()가 다시 얽히는 데드락이 재현되지 않는다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TransferVO executeTransfer(Long transferId) {

        // lockAndMove()는 이미 COMPLETED인 송금이 들어오면 재처리 없이 그 상태를 그대로
        // 반환한다(같은 idempotencyKey로 재시도가 들어온 경우 등). 알림은 "실제로 지금 이
        // 호출이 잔액을 옮겼을 때"만 나가야 하므로, lockAndMove() 호출 *전에* 미리 상태를 봐두고
        // "이번 호출로 방금 완료된 건지" vs "이미 끝난 걸 재확인만 한 건지"를 구분한다.
        TransferVO before = transferMapper.selectById(transferId);
        boolean alreadyCompleted = before != null && "COMPLETED".equals(before.getStatus());

        try {
            TransferVO result = transferExecutor.lockAndMove(transferId);
            if (!alreadyCompleted) {
                notifyAllowanceRecipientBestEffort(result);
            }
            return result;
        } catch (BusinessException e) {
            // TRANSFER_NOT_FOUND는 애초에 존재하지 않는 행을 가리키는 상황이라,
            // 그 행에 FAILED를 기록하려는 시도 자체가 의미가 없다 - 그냥 건너뛴다.
            if (e.getErrorCode() != WalletErrorCode.TRANSFER_NOT_FOUND) {
                // 이 시점엔 transferExecutor.lockAndMove()의 트랜잭션이 이미 완전히 롤백되고
                // 락도 풀린 뒤이므로, markFailed()의 REQUIRES_NEW가 같은 행을 잠글 때
                // 더 이상 아무와도 충돌하지 않는다.
                transferFailureRecorder.markFailed(transferId, e.getErrorCode().getCode());
            }
            throw e;
        } catch (RuntimeException e) {
            // BusinessException이 아닌 예상 못한 예외가 lockAndMove() 도중 발생하면 여기로 떨어진다.
            transferFailureRecorder.markFailed(transferId, CommonErrorCode.COMMON_INTERNAL_ERROR.getCode());
            throw e;
        }
    }

    /**
     * 상위 비즈니스 트랜잭션에 참여하여 송금을 실행한다.
     * 금융상품 승인처럼 계약 상태 변경과 송금이 함께 롤백되어야 할 때 사용한다.
     */
    @Transactional
    public TransferVO executeTransferAtomically(Long transferId) {
        return transferExecutor.lockAndMove(transferId);
    }

    @Transactional
    public void cancelPendingTransfer(Long transferId) {
        if (transferId == null) {
            return;
        }
        TransferVO transfer = transferMapper.selectForUpdate(transferId);
        if (transfer == null) {
            throw new BusinessException(WalletErrorCode.TRANSFER_NOT_FOUND);
        }
        if ("PENDING".equals(transfer.getStatus())) {
            transferMapper.updateStatus(transferId, "CANCELLED", null);
        }
    }

    // 용돈 송금이 실제로 완료됐을 때만 자녀에게 알림을 보낸다. TransferType.ALLOWANCE로
    // 한정하는 이유: executeTransfer()는 지금 시점엔 용돈(1회성/정기)만 타고 들어오지만,
    // 나중에 다른 TransferType이 이 메서드를 타게 되더라도 엉뚱한 문구("용돈이 입금 됐어요")가
    // 잘못 나가지 않도록 명시적으로 막아둔다.
    // referenceId를 null로 고정하는 이유: 실패한 송금은 T_WLT_HIST_H(거래내역 원장)에
    // 애초에 안 남기 때문에 특정 거래를 가리킬 수도 없고 어차피 홈 화면으로 이동시킬 예정
    private void notifyAllowanceRecipientBestEffort(TransferVO transfer) {
        if (!TransferType.ALLOWANCE.name().equals(transfer.getType())) {
            return;
        }
        try {
            WalletVO recipientWallet = walletMapper.selectById(transfer.getToWalletId());
            if (recipientWallet == null) {
                return;
            }
            String content = String.format("%,d원", transfer.getAmount());
            notificationService.createNotification(
                    recipientWallet.getMemberId(), ALLOWANCE_RECEIVED_TITLE, content,
                    NotificationReferenceType.TRANSFER, null, true);
        } catch (RuntimeException notificationException) {
            log.error("용돈 입금 알림 전송 중 오류 - transferId={}", transfer.getId(), notificationException);
        }
    }
}
