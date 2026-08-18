package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.dto.request.SavingPaymentRequestDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.SavingPaymentResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.exception.FinancialProductErrorCode;
import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FreeSavingPaymentVO;
import com.teenyfin.teenymoney.domain.wallet.exception.WalletErrorCode;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.service.TransferService;
import com.teenyfin.teenymoney.domain.wallet.vo.TransferType;
import com.teenyfin.teenymoney.domain.wallet.vo.TransferVO;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
public class FreeSavingPaymentService {
    private final FinancialProductMapper financialProductMapper;
    private final WalletMapper walletMapper;
    private final TransferService transferService;
    private final Clock clock;

    public FreeSavingPaymentService(
            FinancialProductMapper financialProductMapper,
            WalletMapper walletMapper,
            TransferService transferService,
            Clock clock) {
        this.financialProductMapper = financialProductMapper;
        this.walletMapper = walletMapper;
        this.transferService = transferService;
        this.clock = clock;
    }

    /**
     * 송금, 적금 납입 이력 저장을 하나의 트랜잭션으로 처리한다.
     * 뒤 단계가 실패하면 기존 TransferExecutor가 변경한 두 지갑도 함께 롤백된다.
     */
    @Transactional
    public SavingPaymentResponseDTO pay(
            MemberPrincipal principal,
            Long enrollmentId,
            SavingPaymentRequestDTO request) {
        Long childId = requireChild(principal);
        LocalDate paymentDate = LocalDate.now(clock);

        FreeSavingPaymentVO saving = financialProductMapper
                .selectFreeSavingForPaymentForUpdate(childId, enrollmentId);
        if (saving == null) {
            throw new BusinessException(
                    FinancialProductErrorCode.FINANCIAL_PRODUCT_ENROLLMENT_NOT_FOUND);
        }

        // 네트워크 재시도라면 월 한도를 다시 차감하지 않고 최초 성공 결과를 반환한다.
        FreeSavingPaymentVO existing = financialProductMapper
                .selectFreeSavingPaymentByIdempotencyKey(
                        enrollmentId, request.getIdempotencyKey());
        if (existing != null) {
            // 같은 UUID를 다른 금액으로 재사용하는 요청은 기존 송금 규칙과 동일하게 거절한다.
            if (!request.getAmount().equals(existing.getPaidAmount())) {
                throw new BusinessException(
                        WalletErrorCode.IDEMPOTENCY_KEY_CONFLICT);
            }
            return response(existing.getTransferId(), existing.getPaidAmount(),
                    saving.getProductWalletId());
        }

        validatePayable(saving, request.getAmount(), paymentDate);
        WalletVO childWallet = walletMapper.selectMemberWalletByMemberId(childId);
        if (childWallet == null) {
            throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }

        // 기존 송금 엔진에 현재 금융상품 트랜잭션을 참여시켜 출금과 이력을 원자적으로 묶는다.
        TransferVO transfer = transferService.transferInExistingTransaction(
                childWallet.getId(), saving.getProductWalletId(), request.getAmount(),
                TransferType.SAVING, request.getIdempotencyKey());
        financialProductMapper.insertFreeSavingPayment(
                enrollmentId, transfer.getId(), installmentNo(saving, paymentDate),
                request.getAmount());

        return response(transfer.getId(), request.getAmount(),
                saving.getProductWalletId());
    }

    private void validatePayable(FreeSavingPaymentVO saving, long amount,
                                 LocalDate paymentDate) {
        if (!"FREE".equals(saving.getSavingsType())) {
            throw new BusinessException(
                    FinancialProductErrorCode.FINANCIAL_PRODUCT_SAVING_NOT_FREE);
        }
        if (!"ACTIVE".equals(saving.getStatus())
                || saving.getMaturityDate() == null
                || !paymentDate.isBefore(saving.getMaturityDate())) {
            throw new BusinessException(
                    FinancialProductErrorCode.FINANCIAL_PRODUCT_ENROLLMENT_NOT_ACTIVE);
        }
        long paidThisMonth = financialProductMapper
                .selectFreeSavingPaidAmountInMonth(saving.getEnrollmentId(), paymentDate);
        if (amount > saving.getMaxMonthAmount()
                || paidThisMonth > saving.getMaxMonthAmount() - amount) {
            throw new BusinessException(
                    FinancialProductErrorCode.FINANCIAL_PRODUCT_SAVING_MONTHLY_LIMIT_EXCEEDED);
        }
    }

    private int installmentNo(FreeSavingPaymentVO saving, LocalDate paymentDate) {
        if (saving.getStartDate() == null || saving.getPaymentDay() == null) {
            return 1;
        }
        // 승인일보다 납입일이 이미 지났으면 다음 달을 1회차로 본다.
        LocalDate firstPaymentDate = firstPaymentDate(
                saving.getStartDate(), saving.getPaymentDay());
        if (!paymentDate.isAfter(firstPaymentDate)) return 1;
        return Math.toIntExact(ChronoUnit.MONTHS.between(
                firstPaymentDate.withDayOfMonth(1),
                paymentDate.withDayOfMonth(1)) + 1);
    }

    private LocalDate firstPaymentDate(LocalDate startDate, int paymentDay) {
        LocalDate sameMonth = startDate.withDayOfMonth(paymentDay);
        return paymentDay > startDate.getDayOfMonth()
                ? sameMonth : sameMonth.plusMonths(1);
    }

    private SavingPaymentResponseDTO response(
            Long transferId, Long paidAmount, Long productWalletId) {
        WalletVO productWallet = walletMapper.selectWalletForUpdate(productWalletId);
        if (productWallet == null) {
            throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }
        return new SavingPaymentResponseDTO(
                transferId, paidAmount, productWallet.getBalance());
    }

    private Long requireChild(MemberPrincipal principal) {
        if (principal == null || !"CHILD".equals(principal.role())) {
            throw new BusinessException(
                    FinancialProductErrorCode.FINANCIAL_PRODUCT_CHILD_ONLY);
        }
        return principal.memberId();
    }
}
