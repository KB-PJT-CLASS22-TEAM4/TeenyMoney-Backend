package com.teenyfin.teenymoney.domain.allowance.service;


import com.teenyfin.teenymoney.domain.allowance.mapper.AllowanceScheduleMapper;
import com.teenyfin.teenymoney.domain.allowance.vo.AllowanceScheduleVO;
import com.teenyfin.teenymoney.domain.notification.service.NotificationService;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.service.TransferService;
import com.teenyfin.teenymoney.domain.wallet.vo.TransferType;
import com.teenyfin.teenymoney.domain.wallet.vo.TransferVO;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

//정기 용돈 스케줄 하나를 실제로 처리하는 부분만 전담한다
@Service
@Slf4j
// 잡은 예외를 그냥 삼키지만 말고 어딘가엔 남겨야 하는데 로그가 그 역할
public class AllowanceScheduleProcessor {

    private final AllowanceScheduleMapper allowanceScheduleMapper;
    private final WalletMapper walletMapper;
    private final TransferService transferService;
    private final NotificationService notificationService;

    public AllowanceScheduleProcessor(AllowanceScheduleMapper allowanceScheduleMapper, WalletMapper walletMapper, TransferService transferService, NotificationService notificationService) {
        this.allowanceScheduleMapper = allowanceScheduleMapper;
        this.walletMapper = walletMapper;
        this.transferService = transferService;
        this.notificationService = notificationService;
    }

    public void process(Long scheduleId, LocalDate paymentDate) {
        AllowanceScheduleVO schedule = allowanceScheduleMapper.selectById(scheduleId);
        // 배치가 도는 사이 삭제되었거나 비활성화 시 그냥 건너뜀
        if (schedule == null || !schedule.isActive()) {
            return;
        }

        WalletVO parentWallet = walletMapper.selectMemberWalletByMemberId(schedule.getParentId());
        WalletVO childWallet = walletMapper.selectMemberWalletByMemberId(schedule.getChildId());

        if(parentWallet == null || childWallet == null) {
            // 지갑이 없는 비정상 상태
            return;
        }


        // 스케줄러가 트리거하는 거라 클라이언트가 멱등키를 안 줌 - 서버가 결정론적으로 생성.
        // 같은 스케줄이 같은 날 두 번 배치 도는(재시도 등) 상황에서도 TransferService가
        // 이 키로 중복 지급을 걸러줌.

        String idempotencyKey = "ALLOWANCE_SCHEDULE:" + scheduleId + ":" + paymentDate;
        TransferVO pending = null;

        try {
            pending = transferService.createPendingTransfer(parentWallet.getId(), childWallet.getId(), schedule.getAmount(),
                    TransferType.ALLOWANCE, idempotencyKey);
            transferService.executeTransfer(pending.getId());
        } catch (BusinessException e) {
            // TransferService.executeTransfer()가 실패 시 T_WLT_TRF_L에 FAILED+failure_reason을
            // 이미 자동 기록했으므로, 여기서는 부모에게 알리기만 하면 된다.
            Long transferId = pending != null ? pending.getId() : null;
            notificationService.createNotification(
                    schedule.getParentId(), "정기 용돈 지급에 실패했어요", e.getErrorCode().getMessage(),NotificationReferenceType.TRANSFER,
                    transferId, true);
        } catch (RuntimeException e) {
            // 새로 추가: BusinessException이 아닌 "예상 못한" 예외 전부 여기서 잡는다
            // (DB 순간 장애 등). 구체적인 실패 사유를 모르니 e.getErrorCode() 같은 건 못 쓰고,
            // 로그로 원인을 남기고 부모에게는 일반화된 메시지로 알린다.
            // 여기서 잡아야 아래 next_payment_date 갱신 줄까지 반드시 도달한다는 게 핵심.
            Long transferId = pending != null ? pending.getId() : null;
            log.error("정기 용돈 스케줄 처리 중 예상하지 못한 오류 - scheduleId={}", scheduleId, e);
            notificationService.createNotification(
                    schedule.getParentId(), "정기 용돈 지급에 실패했어요", "일시적인 오류로 지급에 실패했어요.",
                    NotificationReferenceType.TRANSFER, transferId, true);
        }

        // 이제 위 세 경로(성공/BusinessException/RuntimeException) 전부 반드시 여기까지 옴
        LocalDate nextPaymentDate = AllowanceScheduleDateCalculator.calculateNext(
                paymentDate, schedule.getCycleType(), schedule.getPaymentDay());
        allowanceScheduleMapper.updateNextPaymentDate(scheduleId, nextPaymentDate);
    }
}
