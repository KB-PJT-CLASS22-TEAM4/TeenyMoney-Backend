package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.family.service.FamilyAccessService;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.DepositCompletionPeriodResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.FinancialProductCompletionDetailResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.LoanCompletionRepaymentResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.SavingCompletionPaymentResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.exception.FinancialProductErrorCode;
import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductEnrollmentVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductSettlementVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductType;
import com.teenyfin.teenymoney.domain.financialproduct.vo.LoanRepaymentHistoryVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.SavingPaymentHistoryVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class FinancialProductCompletionService {
    private final FinancialProductMapper mapper;
    private final FamilyAccessService familyAccessService;
    private final FinancialProductInterestCalculator interestCalculator;

    public FinancialProductCompletionService(
            FinancialProductMapper mapper,
            FamilyAccessService familyAccessService,
            FinancialProductInterestCalculator interestCalculator) {
        this.mapper = mapper;
        this.familyAccessService = familyAccessService;
        this.interestCalculator = interestCalculator;
    }

    @Transactional(readOnly = true)
    public FinancialProductCompletionDetailResponseDTO getMyCompletionDetail(
            MemberPrincipal principal, String productType, Long enrollmentId) {
        if (principal == null || !"CHILD".equals(principal.role())) {
            throw new BusinessException(FinancialProductErrorCode.FINANCIAL_PRODUCT_CHILD_ONLY);
        }
        return detail(principal.memberId(), productType, enrollmentId);
    }

    @Transactional(readOnly = true)
    public FinancialProductCompletionDetailResponseDTO getChildCompletionDetail(
            MemberPrincipal principal, Long childId, String productType, Long enrollmentId) {
        if (principal == null || !"PARENT".equals(principal.role())) {
            throw new BusinessException(FinancialProductErrorCode.FINANCIAL_PRODUCT_PARENT_ONLY);
        }
        familyAccessService.requireChildAccess(principal, childId);
        return detail(childId, productType, enrollmentId);
    }

    private FinancialProductCompletionDetailResponseDTO detail(
            Long childId, String productType, Long enrollmentId) {
        FinancialProductType type = FinancialProductType.from(productType);
        FinancialProductEnrollmentVO enrollment = selectEnrollment(type, childId, enrollmentId);
        if (enrollment == null) {
            throw new BusinessException(
                    FinancialProductErrorCode.FINANCIAL_PRODUCT_ENROLLMENT_NOT_FOUND);
        }
        requireCompleted(type, enrollment.getStatus());
        return switch (type) {
            case DEPOSIT -> depositDetail(enrollment);
            case SAVING -> savingDetail(enrollment);
            case LOAN -> loanDetail(enrollment);
        };
    }

    private FinancialProductEnrollmentVO selectEnrollment(
            FinancialProductType type, Long childId, Long enrollmentId) {
        return switch (type) {
            case DEPOSIT -> mapper.selectDepositEnrollmentByChildIdAndId(childId, enrollmentId);
            case SAVING -> mapper.selectSavingEnrollmentByChildIdAndId(childId, enrollmentId);
            case LOAN -> mapper.selectLoanEnrollmentByChildIdAndId(childId, enrollmentId);
        };
    }

    private void requireCompleted(FinancialProductType type, String status) {
        // 중도해지는 실행 API가 결과를 즉시 반환하므로 이 조회 범위에 포함하지 않는다.
        // 이 API는 정상 만기된 예·적금과 완납된 대출을 나중에 다시 보는 용도다.
        boolean completed = type == FinancialProductType.LOAN
                ? "REPAID".equals(status) : "MATURED".equals(status);
        if (!completed) {
            throw new BusinessException(
                    FinancialProductErrorCode.FINANCIAL_PRODUCT_COMPLETION_DETAIL_NOT_AVAILABLE);
        }
    }

    private FinancialProductCompletionDetailResponseDTO depositDetail(
            FinancialProductEnrollmentVO enrollment) {
        // 완료 뒤 상품 지갑 잔액은 0원이므로 지갑 잔액이 아니라 만기 정산 송금을 원금·이자로 조회한다.
        FinancialProductSettlementVO settlement = settlement("DPT", enrollment.getEnrollmentId());
        List<DepositCompletionPeriodResponseDTO> periods = depositPeriods(enrollment, settlement);
        return base(enrollment, settlement.getPrincipalAmount(), settlement.getInterestAmount())
                .completionType("NORMAL_MATURITY")
                .depositPeriods(periods)
                .savingPayments(Collections.emptyList())
                .loanRepayments(Collections.emptyList())
                .build();
    }

    private FinancialProductCompletionDetailResponseDTO savingDetail(
            FinancialProductEnrollmentVO enrollment) {
        // 총액은 실제 만기 송금을 기준으로 하고, 회차 목록은 납입 원장에서 가져온다.
        FinancialProductSettlementVO settlement = settlement("SVG", enrollment.getEnrollmentId());
        List<SavingPaymentHistoryVO> histories =
                mapper.selectSavingPaymentHistories(enrollment.getEnrollmentId());
        List<Long> interests = allocateSavingInterest(enrollment, histories,
                settlement.getInterestAmount());
        List<SavingCompletionPaymentResponseDTO> payments = new ArrayList<>();
        for (int index = 0; index < histories.size(); index++) {
            SavingPaymentHistoryVO history = histories.get(index);
            payments.add(new SavingCompletionPaymentResponseDTO(
                    history.getInstallmentNo(), value(history.getScheduledAmount()),
                    value(history.getPaidAmount()), history.getStatus(), history.getPaidAt(),
                    history.getCreatedAt(), interests.get(index)));
        }
        return base(enrollment, settlement.getPrincipalAmount(), settlement.getInterestAmount())
                .completionType("NORMAL_MATURITY")
                .depositPeriods(Collections.emptyList())
                .savingPayments(payments)
                .loanRepayments(Collections.emptyList())
                .build();
    }

    private FinancialProductCompletionDetailResponseDTO loanDetail(
            FinancialProductEnrollmentVO enrollment) {
        List<LoanRepaymentHistoryVO> histories =
                mapper.selectLoanRepaymentHistories(enrollment.getEnrollmentId());
        List<LoanCompletionRepaymentResponseDTO> repayments = histories.stream()
                .map(history -> new LoanCompletionRepaymentResponseDTO(
                        displayInstallmentNo(history), history.getRepaymentType(),
                        dueDate(enrollment, history), value(history.getPrincipalAmount()),
                        value(history.getPaidPrincipalAmount()), value(history.getInterestAmount()),
                        value(history.getPaidInterestAmount()), history.getStatus(),
                        history.getOverdueStartAt(), history.getPaidAt(), history.getCreatedAt()))
                .toList();
        long paidPrincipal = histories.stream()
                .mapToLong(history -> value(history.getPaidPrincipalAmount())).sum();
        long paidInterest = histories.stream()
                .mapToLong(history -> value(history.getPaidInterestAmount())).sum();
        LocalDateTime completedAt = histories.stream()
                .map(LoanRepaymentHistoryVO::getCreatedAt)
                .filter(java.util.Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
        boolean early = histories.stream().anyMatch(h -> "EARLY".equals(h.getRepaymentType()));
        boolean overdue = histories.stream().anyMatch(h ->
                "OVERDUE".equals(h.getStatus()) || "PARTIAL".equals(h.getStatus()));
        return base(enrollment, paidPrincipal, paidInterest)
                .completedAt(completedAt)
                .completionType(early ? "EARLY_REPAID"
                        : overdue ? "REPAID_AFTER_OVERDUE" : "NORMAL_REPAID")
                .depositPeriods(Collections.emptyList())
                .savingPayments(Collections.emptyList())
                .loanRepayments(repayments)
                .build();
    }

    private FinancialProductCompletionDetailResponseDTO.FinancialProductCompletionDetailResponseDTOBuilder
            base(FinancialProductEnrollmentVO enrollment, long principal, long interest) {
        return FinancialProductCompletionDetailResponseDTO.builder()
                .enrollmentId(enrollment.getEnrollmentId())
                .productType(enrollment.getProductType())
                .productName(enrollment.getProductName())
                .status(enrollment.getStatus())
                .termMonths(enrollment.getTermMonths())
                .startDate(enrollment.getStartDate())
                .maturityDate(enrollment.getMaturityDate())
                .completedAt(enrollment.getClosedAt())
                .appliedRate(enrollment.getAppliedRate())
                .principalAmount(principal)
                .interestAmount(interest)
                .totalAmount(Math.addExact(principal, interest));
    }

    private FinancialProductSettlementVO settlement(String code, Long enrollmentId) {
        // 만기 처리기가 사용하는 멱등성 키(DPT_MAT:{id}:P/I, SVG_MAT:{id}:P/I)를 그대로 조회한다.
        return mapper.selectCompletedSettlement(code + "_MAT:" + enrollmentId);
    }

    private List<DepositCompletionPeriodResponseDTO> depositPeriods(
            FinancialProductEnrollmentVO enrollment, FinancialProductSettlementVO settlement) {
        // 예금에는 월별 원장이 없으므로 가입 당시 고정된 금리·계산방식으로 누적 값을 재구성한다.
        // 마지막 행은 실제 만기 이자 송금액으로 맞춰 화면 합계와 실제 지급액이 어긋나지 않게 한다.
        List<DepositCompletionPeriodResponseDTO> periods = new ArrayList<>();
        for (int month = 1; month <= enrollment.getTermMonths(); month++) {
            LocalDate end = enrollment.getStartDate().plusMonths(month);
            if (end.isAfter(enrollment.getMaturityDate())) end = enrollment.getMaturityDate();
            long interest = month == enrollment.getTermMonths()
                    ? settlement.getInterestAmount()
                    : interestCalculator.calculate(settlement.getPrincipalAmount(),
                            enrollment.getAppliedRate(), enrollment.getInterestCalculationType(),
                            enrollment.getStartDate(), end);
            periods.add(new DepositCompletionPeriodResponseDTO(
                    month, end, settlement.getPrincipalAmount(), interest,
                    Math.addExact(settlement.getPrincipalAmount(), interest)));
        }
        return periods;
    }

    private List<Long> allocateSavingInterest(
            FinancialProductEnrollmentVO enrollment, List<SavingPaymentHistoryVO> histories,
            long actualTotalInterest) {
        // 적금 이자는 만기 때 한 번에 지급되어 회차별 이자 컬럼이 없다.
        // 각 납입금의 예치기간별 계산값을 가중치로 실제 총이자를 배분하고, 원 단위 나머지는
        // 마지막 정상 납입에 더해 회차별 합계가 실제 이자 송금과 정확히 일치하게 한다.
        List<Long> weights = new ArrayList<>();
        LocalDate end = enrollment.getMaturityDate();
        long totalWeight = 0;
        int lastPaidIndex = -1;
        for (int index = 0; index < histories.size(); index++) {
            SavingPaymentHistoryVO history = histories.get(index);
            long weight = history.getPaidAt() == null ? 0
                    : interestCalculator.calculate(value(history.getPaidAmount()),
                            enrollment.getAppliedRate(), enrollment.getInterestCalculationType(),
                            history.getPaidAt().toLocalDate(), end);
            if (history.getPaidAt() != null && value(history.getPaidAmount()) > 0) lastPaidIndex = index;
            weights.add(weight);
            totalWeight = Math.addExact(totalWeight, weight);
        }
        List<Long> result = new ArrayList<>();
        long allocated = 0;
        for (long weight : weights) {
            long interest = totalWeight == 0 ? 0 : BigDecimal.valueOf(actualTotalInterest)
                    .multiply(BigDecimal.valueOf(weight))
                    .divide(BigDecimal.valueOf(totalWeight), 0, RoundingMode.DOWN)
                    .longValueExact();
            result.add(interest);
            allocated = Math.addExact(allocated, interest);
        }
        if (lastPaidIndex >= 0) {
            result.set(lastPaidIndex, Math.addExact(
                    result.get(lastPaidIndex), actualTotalInterest - allocated));
        }
        return result;
    }

    private LocalDate dueDate(
            FinancialProductEnrollmentVO enrollment, LoanRepaymentHistoryVO history) {
        if ("EARLY".equals(history.getRepaymentType()) || history.getInstallmentNo() <= 0) return null;
        LocalDate sameMonth = enrollment.getStartDate()
                .withDayOfMonth(enrollment.getPaymentDay());
        LocalDate first = sameMonth.isAfter(enrollment.getStartDate())
                ? sameMonth : sameMonth.plusMonths(1);
        return first.plusMonths(history.getInstallmentNo() - 1L);
    }

    private Integer displayInstallmentNo(LoanRepaymentHistoryVO history) {
        // DB에서는 정규 회차(1~N)와 충돌하지 않도록 조기상환을 0회차로 저장하지만,
        // API 소비자에게 0회차는 실제 회차처럼 보이므로 null로 숨기고 repaymentType으로 구분한다.
        return "EARLY".equals(history.getRepaymentType())
                ? null : history.getInstallmentNo();
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }
}
