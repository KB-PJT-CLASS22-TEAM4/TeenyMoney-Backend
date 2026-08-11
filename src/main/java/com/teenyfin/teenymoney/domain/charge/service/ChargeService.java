package com.teenyfin.teenymoney.domain.charge.service;


import com.teenyfin.teenymoney.domain.charge.crypto.BillingKeyEncryptor;
import com.teenyfin.teenymoney.domain.charge.exception.ChargeErrorCode;
import com.teenyfin.teenymoney.domain.charge.mapper.ChargeMapper;
import com.teenyfin.teenymoney.domain.charge.mapper.ChargeMethodMapper;
import com.teenyfin.teenymoney.domain.charge.toss.TossPaymentsClient;
import com.teenyfin.teenymoney.domain.charge.toss.dto.TossBillingApproveRequestDTO;
import com.teenyfin.teenymoney.domain.charge.toss.dto.TossPaymentApprovalResponseDTO;
import com.teenyfin.teenymoney.domain.charge.vo.ChargeMethodVO;
import com.teenyfin.teenymoney.domain.charge.vo.ChargeVO;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import com.teenyfin.teenymoney.domain.wallet.exception.WalletErrorCode;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.exception.CommonErrorCode;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

// 충전 전체 흐름의 지휘자. createPendingCharge()/executeCharge() 둘 다 여기 있고,
// 실제 락+크레딧은 ChargeExecutor, 실패 기록은 ChargeFailureRecorder한테 위임한다 -
// TransferService가 TransferExecutor/TransferFailureRecorder를 오케스트레이션만
// 하는 것과 같은 구조.
@Slf4j
@Service
public class ChargeService {

    private final ChargeMapper chargeMapper;
    private final ChargeMethodMapper chargeMethodMapper;
    private final WalletMapper walletMapper;
    private final MemberMapper memberMapper;
    private final TossPaymentsClient tossPaymentsClient;
    private final BillingKeyEncryptor billingKeyEncryptor;
    private final ChargeExecutor chargeExecutor;
    private final ChargeFailureRecorder chargeFailureRecorder;

    public ChargeService(ChargeMapper chargeMapper, ChargeMethodMapper chargeMethodMapper, WalletMapper walletMapper, MemberMapper memberMapper, TossPaymentsClient tossPaymentsClient, BillingKeyEncryptor billingKeyEncryptor, ChargeExecutor chargeExecutor, ChargeFailureRecorder chargeFailureRecorder) {
        this.chargeMapper = chargeMapper;
        this.chargeMethodMapper = chargeMethodMapper;
        this.walletMapper = walletMapper;
        this.memberMapper = memberMapper;
        this.tossPaymentsClient = tossPaymentsClient;
        this.billingKeyEncryptor = billingKeyEncryptor;
        this.chargeExecutor = chargeExecutor;
        this.chargeFailureRecorder = chargeFailureRecorder;
    }


    // 1단계: 충전을 "PENDING" 상태로 접수만 해둔다. 토스는 아직 안 부른다.
    // walletId를 파라미터로 안 받는 이유: 부모는 지갑이 하나뿐이라, principal로
    // 서버가 직접 조회한다 (클라이언트가 남의 walletId를 넣을 수 있는 취약점 자체를 없앰).
    @Transactional
    public ChargeVO createPendingCharge(MemberPrincipal principal, Long paymentMethodId, Long amount, String idempotencyKey) {
        Long parentId = principal.memberId();

        if (amount == null || amount <= 0) {
            throw new BusinessException(ChargeErrorCode.INVALID_CHARGE_AMOUNT);
        }
        if (idempotencyKey == null) {
            throw new BusinessException(ChargeErrorCode.INVALID_IDEMPOTENCY_KEY);
        }

        // 로그인한 부모 자신의 지갑을 조회. WalletMapper.selectMemberWalletByMemberId는
        // 부모당 지갑이 하나뿐이라는 전제로 단건을 리턴한다.
        WalletVO wallet = walletMapper.selectMemberWalletByMemberId(parentId);
        if (wallet == null) {
            throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }

        // 결제수단이 진짜 이 부모 것 맞는지 + ACTIVE인지 확인.
        // ChargeMethodService.findOwnedChargeMethodOrThrow()가 private이라 직접 못 부르고,
        // 같은 검증 로직을 여기에도 똑같이 둔다(패턴만 재사용, 코드는 별도).
        ChargeMethodVO method = findOwnedActivePaymentMethodOrThrow(parentId, paymentMethodId);

        // 같은 idempotencyKey로 이미 접수된 충전이 있으면 새로 안 만들고 그걸 그대로 반환.
        ChargeVO existing = chargeMapper.selectByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            ensureMatchesRequestOrThrow(existing, wallet.getId(), paymentMethodId, amount);
            return existing;
        }

        ChargeVO charge = new ChargeVO();
        charge.setWalletId(wallet.getId());
        charge.setPaymentMethodId(paymentMethodId);
        charge.setAmount(amount);
        charge.setIdempotencyKey(idempotencyKey);
        // 토스에 보낼 주문번호를 여기서 딱 한 번 발급. 이후 executeCharge()가 재시도해도
        // 이 값은 절대 안 바뀌고, Idempotency-Key 헤더값으로도 그대로 재사용된다.
        charge.setOrderId(UUID.randomUUID().toString());

        try {
            chargeMapper.insertCharge(charge);
        } catch (DuplicateKeyException e) {
            // 동시에 같은 idempotencyKey로 두 요청이 왔을 때, 경쟁에서 진 쪽이 여기로 옴.
            ChargeVO winner = chargeMapper.selectByIdempotencyKey(idempotencyKey);
            if (winner == null) {
                throw new BusinessException(ChargeErrorCode.IDEMPOTENCY_KEY_CONFLICT);
            }
            ensureMatchesRequestOrThrow(winner, wallet.getId(), paymentMethodId, amount);
            return winner;
        } catch (DataIntegrityViolationException e) {
            // amount/idempotencyKey는 이미 검증했고 wallet/결제수단도 방금 확인했으니
            // 여기 걸릴 확률은 극히 낮음(그 사이 지갑/결제수단이 삭제되는 등 극단적 레이스) -
            // TransferService.createPendingTransfer()와 같은 이유로 방어만 해둔다.
            throw new BusinessException(ChargeErrorCode.CHARGE_METHOD_NOT_FOUND);
        }

        charge.setStatus("PENDING");
        return charge;
    }

    // 2단계: 실제로 토스에 승인을 요청하고, 결과에 따라 지갑 크레딧 또는 실패 처리한다.
    // NOT_SUPPORTED: 이 메서드는 절대 트랜잭션 안에서 실행되면 안 된다는 걸 코드로 강제한다.
    // TransferService.executeTransfer()와 같은 이유 - chargeExecutor.lockAndCredit()의
    // 트랜잭션과 markFailed() 호출이 서로 얽히면 데드락 위험이 있다 (ChargeExecutor 만들 때 자세히 설명한 내용).
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ChargeVO executeCharge(Long chargeId) {
        ChargeVO charge = chargeMapper.selectById(chargeId);
        if (charge == null) {
            throw new BusinessException(ChargeErrorCode.CHARGE_NOT_FOUND);
        }
        if ("SUCCESS".equals(charge.getStatus())) {
            return charge;
        }

        // 선점: PENDING -> PROCESSING. 0행 영향이면 이미 다른 요청이 먼저 선점한 것이므로
        // 여기서 멈춘다 (우리 서버 내부 동시 실행 방어 - Idempotency-Key랑은 다른 문제).
        if (chargeMapper.claimForProcessing(chargeId) == 0) {
            ChargeVO current = chargeMapper.selectById(chargeId);
            if(current != null && "SUCCESS".equals(current.getStatus())) {
                return current;
            }
            throw new BusinessException(ChargeErrorCode.CHARGE_ALREADY_PROCESSING);
        }
        //결제 수단이 접수 이후 그 사이에 삭재됐을 수도 있으니 실행 직전에 다시 확인
        ChargeMethodVO method = chargeMethodMapper.selectById(charge.getPaymentMethodId());
        if(method == null || !"ACTIVE".equals(method.getStatus())) {
            chargeFailureRecorder.markFailed(chargeId, ChargeErrorCode.CHARGE_METHOD_NOT_FOUND.getCode());
            throw new BusinessException(ChargeErrorCode.CHARGE_METHOD_NOT_FOUND);
        }

        String billingKey = billingKeyEncryptor.decrypt(method.getBillingKey());
        MemberVO parent = memberMapper.selectById(method.getParentId());

        TossPaymentApprovalResponseDTO tossResponse;

        try{
            // Idempotency-Key로 charge.orderId를 그대로 재사용 - 같은 시도를 재시도해도
            // 토스가 원래 결과를 그대로 돌려주게 하기 위함 (ChargeVO.orderId 주석 참고).
            tossResponse = tossPaymentsClient.approveBillingPayment(billingKey, new TossBillingApproveRequestDTO(parent.getCustomerKey(), charge.getAmount(),
                    charge.getOrderId(), "TeenyMoney 충전"), charge.getOrderId());
        } catch(BusinessException e) {
            if (e.getErrorCode() == ChargeErrorCode.TOSS_REQUEST_IN_PROGRESS) {
                // 성공도 실패도 아닌 "아직 처리 중" 상태 - markFailed() 부르면 안 되고,
                // PROCESSING 그대로 두고 그대로 위로 던진다.
                throw e;
            }
            if (e.getErrorCode() == ChargeErrorCode.TOSS_ORDER_ID_DUPLICATED) {
                // 정상 흐름이면 볼 일 없는 케이스 - 혹시 다른 스레드가 먼저 성공시켜놨는지 확인.
                ChargeVO current = chargeMapper.selectById(chargeId);
                if (current != null && "SUCCESS".equals(current.getStatus())) {
                    return current;
                }
                chargeFailureRecorder.markFailed(chargeId, e.getErrorCode().getCode());
                throw new BusinessException(ChargeErrorCode.CHARGE_STATE_CONFLICT);
            }
            chargeFailureRecorder.markFailed(chargeId, e.getErrorCode().getCode());
            throw e;
        } catch (RestClientException e) {
            // approveBillingPayment()가 못 잡는 케이스: 연결이 아예 안 되거나 타임아웃나서
            // 토스 응답 자체를 못 받은 상황. 실제로 승인이 됐는지 안 됐는지 우리도 모르므로
            // 실패로 단정하지 않는다 - PROCESSING 그대로 두고, 나중에 같은 orderId+
            // Idempotency-Key로 재시도하는 절차(지금은 미구현, 추후 과제)로 복구해야 한다.
            log.warn("토스 자동결제 승인 통신 실패 - chargeId={}, orderId={}", chargeId, charge.getOrderId(), e);
            throw new BusinessException(ChargeErrorCode.TOSS_REQUEST_IN_PROGRESS);

        }

        try {
            return chargeExecutor.lockAndCredit(chargeId, tossResponse.getPaymentKey());
        } catch (RuntimeException e) {
            // 토스 승인은 이미 성공해서 카드값이 나갔는데, 우리 DB 처리가 실패한 상황.
            // 그대로 두면 "카드값은 나갔는데 지갑엔 안 들어온" 최악의 상태가 되므로
            // 방금 승인된 결제를 즉시 취소해서 되돌린다 (best-effort 보상).
            try {
                tossPaymentsClient.cancelPayment(
                        tossResponse.getPaymentKey(), "내부 오류로 자동 취소", charge.getOrderId() + "-cancel");
            } catch (RuntimeException cancelException) {
                log.warn("고아 결제 정리 실패 - paymentKey={}", tossResponse.getPaymentKey(), cancelException);
            }
            chargeFailureRecorder.markFailed(chargeId, CommonErrorCode.COMMON_INTERNAL_ERROR.getCode());
            throw e;
        }



    }


    // id로 결제수단을 조회하고, 존재+ACTIVE+소유권까지 같이 검증하는 헬퍼.
    // ChargeMethodService.findOwnedChargeMethodOrThrow()와 거의 같은 로직인데,
    // 거긴 삭제(INACTIVE) 여부만 봤다면 여기선 애초에 ACTIVE인 것만 통과시킨다는 점이 같다.
    private ChargeMethodVO findOwnedActivePaymentMethodOrThrow(Long parentId, Long paymentMethodId) {
        ChargeMethodVO method = chargeMethodMapper.selectById(paymentMethodId);
        if (method == null || !"ACTIVE".equals(method.getStatus())) {
            throw new BusinessException(ChargeErrorCode.CHARGE_METHOD_NOT_FOUND);
        }
        if (!method.getParentId().equals(parentId)) {
            throw new BusinessException(ChargeErrorCode.CHARGE_METHOD_ACCESS_DENIED);
        }
        return method;
    }
    // 같은 idempotencyKey로 이미 존재하는 행이, 지금 들어온 요청과 내용이 같은지 확인.
    // 다르면 "이 키는 이미 다른 내용으로 쓰였다"는 뜻이라 예외를 던진다.
    private void ensureMatchesRequestOrThrow(ChargeVO existing, Long walletId, Long paymentMethodId, Long amount) {
        if (!existing.getWalletId().equals(walletId)
                || !existing.getPaymentMethodId().equals(paymentMethodId)
                || !existing.getAmount().equals(amount)) {
            throw new BusinessException(ChargeErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
    }
}
