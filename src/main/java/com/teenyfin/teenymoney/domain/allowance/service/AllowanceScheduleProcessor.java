package com.teenyfin.teenymoney.domain.allowance.service;


import com.teenyfin.teenymoney.domain.allowance.mapper.AllowanceScheduleMapper;
import com.teenyfin.teenymoney.domain.allowance.vo.AllowanceScheduleVO;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
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
    private final MemberMapper memberMapper;

    public AllowanceScheduleProcessor(AllowanceScheduleMapper allowanceScheduleMapper, WalletMapper walletMapper, TransferService transferService, NotificationService notificationService, MemberMapper memberMapper) {
        this.allowanceScheduleMapper = allowanceScheduleMapper;
        this.walletMapper = walletMapper;
        this.transferService = transferService;
        this.notificationService = notificationService;
        this.memberMapper = memberMapper;
    }

    public void process(Long scheduleId, LocalDate paymentDate) {
        AllowanceScheduleVO schedule = allowanceScheduleMapper.selectById(scheduleId);
        if (schedule == null || !schedule.isActive()) {
            return;
        }

        // next_payment_date가 오늘보다 과거면 - 서버가 그 날짜에 배치를 못 돌렸던 경우다.
        // 밀린 회차는 뒤늦게 실제로 지급하지 않는다("밀린 회차를 몰아서 처리하지 않는다"는
        // 기존 원칙을 지급 시도 자체에도 적용) - 미지급으로 알리고 다음 정상 주기로 넘어간다.
        if (schedule.getNextPaymentDate().isBefore(paymentDate)) {
            log.warn("정기 용돈 스케줄 처리 실패 - scheduleId={}, reason=배치 지연으로 이번 회차 미지급", scheduleId);
            notifyBestEffort(scheduleId, schedule, null);
            advanceToNextCycle(scheduleId, schedule, paymentDate);
            return;
        }

        WalletVO parentWallet = walletMapper.selectMemberWalletByMemberId(schedule.getParentId());
        WalletVO childWallet = walletMapper.selectMemberWalletByMemberId(schedule.getChildId());
        if (parentWallet == null || childWallet == null) {
            return;
        }

        String idempotencyKey = "ALLOWANCE_SCHEDULE:" + scheduleId + ":" + paymentDate;
        TransferVO pending = null;

        try {
            pending = transferService.createPendingTransfer(parentWallet.getId(), childWallet.getId(), schedule.getAmount(),
                    TransferType.ALLOWANCE, idempotencyKey);
            transferService.executeTransfer(pending.getId());
        } catch (BusinessException e) {
            Long transferId = pending != null ? pending.getId() : null;
            log.warn("정기 용돈 스케줄 처리 실패 - scheduleId={}, reason={}", scheduleId, e.getErrorCode().getMessage());
            notifyBestEffort(scheduleId, schedule, transferId);
        } catch (RuntimeException e) {
            Long transferId = pending != null ? pending.getId() : null;
            log.error("정기 용돈 스케줄 처리 중 예상하지 못한 오류 - scheduleId={}", scheduleId, e);
            notifyBestEffort(scheduleId, schedule, transferId);
        }

        advanceToNextCycle(scheduleId, schedule, paymentDate);
    }

    private void advanceToNextCycle(Long scheduleId, AllowanceScheduleVO schedule, LocalDate paymentDate) {
        LocalDate nextPaymentDate = AllowanceScheduleDateCalculator.calculateNext(
                paymentDate, schedule.getCycleType(), schedule.getPaymentDay());
        allowanceScheduleMapper.updateNextPaymentDate(scheduleId, nextPaymentDate);
    }

    // 알림은 best-effort다 - createNotification() 자체가 DB/FCM 문제로 예외를 던져도
    // 그게 배치 진행(next_payment_date 갱신)을 막으면 안 되므로 여기서 자체적으로 격리한다.
    private void notifyBestEffort(Long scheduleId, AllowanceScheduleVO schedule, Long transferId) {
        try {
            MemberVO child = memberMapper.selectById(schedule.getChildId());

            // 자녀 조회가 안 되면(탈퇴 등) 누구 건인지 특정할 수 없으므로 알림 자체를 보내지 않는다.
            if (child == null) {
                log.warn("정기 용돈 실패 알림 스킵 - 자녀 조회 실패, scheduleId={}, childId={}",
                        scheduleId, schedule.getChildId());
                return;
            }

            String content = child.getName() + " " + String.format("%,d원", schedule.getAmount());
            notificationService.createNotification(
                    schedule.getParentId(), "정기 용돈 지급이 실패됐어요", content,
                    NotificationReferenceType.TRANSFER, transferId, true);
        } catch (RuntimeException notificationException) {
            log.error("정기 용돈 실패 알림 전송 중 오류 - scheduleId={}", scheduleId, notificationException);
        }
    }
}
