package com.teenyfin.teenymoney.domain.financialproduct.mapper;

import com.teenyfin.teenymoney.domain.financialproduct.vo.DepositProductVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductBenefitVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductEnrollmentVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductEnrollmentCommandVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductApprovalVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.LoanProductVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.SavingProductVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FreeSavingPaymentVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductMaturityVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FreeSavingMonthlyVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.SavingContributionVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductTerminationVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.LoanRepaymentVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.LoanEarlyRepaymentHistoryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Mapper
public interface FinancialProductMapper {
    List<DepositProductVO> selectActiveDepositProducts();
    List<SavingProductVO> selectActiveSavingProducts();
    List<LoanProductVO> selectActiveLoanProducts();
    // 공용 상품과 로그인 회원에게 허용된 부모 상품만 조회한다.
    List<DepositProductVO> selectVisibleDepositProducts(@Param("memberId") Long memberId);
    List<SavingProductVO> selectVisibleSavingProducts(@Param("memberId") Long memberId);
    List<LoanProductVO> selectVisibleLoanProducts(@Param("memberId") Long memberId);
    DepositProductVO selectActiveDepositProductById(@Param("id") Long id);
    SavingProductVO selectActiveSavingProductById(@Param("id") Long id);
    LoanProductVO selectActiveLoanProductById(@Param("id") Long id);
    DepositProductVO selectVisibleDepositProductById(@Param("id") Long id,
                                                     @Param("memberId") Long memberId);
    SavingProductVO selectVisibleSavingProductById(@Param("id") Long id,
                                                   @Param("memberId") Long memberId);
    LoanProductVO selectVisibleLoanProductById(@Param("id") Long id,
                                               @Param("memberId") Long memberId);
    // 부모가 입력한 조건으로 자녀 전용 상품을 저장한다.
    int insertCustomDepositProduct(DepositProductVO product);
    int insertCustomSavingProduct(SavingProductVO product);
    int insertCustomLoanProduct(LoanProductVO product);
    // 삭제 대상 소유권(부모·대상 자녀)까지 함께 확인하며 잠근다.
    DepositProductVO selectCustomDepositProductForDelete(
            @Param("parentId") Long parentId, @Param("childId") Long childId,
            @Param("productId") Long productId);
    SavingProductVO selectCustomSavingProductForDelete(
            @Param("parentId") Long parentId, @Param("childId") Long childId,
            @Param("productId") Long productId);
    LoanProductVO selectCustomLoanProductForDelete(
            @Param("parentId") Long parentId, @Param("childId") Long childId,
            @Param("productId") Long productId);
    // 승인 대기·가입 중(대출은 연체·미상환 포함) 신청이 남아 있으면 삭제를 막는 데 쓴다.
    int countOpenDepositEnrollmentsByProductId(@Param("productId") Long productId);
    int countOpenSavingEnrollmentsByProductId(@Param("productId") Long productId);
    int countOpenLoanEnrollmentsByProductId(@Param("productId") Long productId);
    int deactivateCustomDepositProduct(@Param("productId") Long productId);
    int deactivateCustomSavingProduct(@Param("productId") Long productId);
    int deactivateCustomLoanProduct(@Param("productId") Long productId);
    // 자녀가 승인 대기 중인 본인 신청을 취소한다. 조건에 안 걸리면 0을 반환한다.
    int cancelDepositEnrollment(@Param("childId") Long childId,
                               @Param("enrollmentId") Long enrollmentId);
    int cancelSavingEnrollment(@Param("childId") Long childId,
                              @Param("enrollmentId") Long enrollmentId);
    int cancelLoanEnrollment(@Param("childId") Long childId,
                            @Param("enrollmentId") Long enrollmentId);
    int countGradeById(@Param("gradeId") Long gradeId);
    List<FinancialProductEnrollmentVO> selectDepositEnrollmentsByChildId(
            @Param("childId") Long childId);
    List<FinancialProductEnrollmentVO> selectSavingEnrollmentsByChildId(
            @Param("childId") Long childId);
    List<FinancialProductEnrollmentVO> selectLoanEnrollmentsByChildId(
            @Param("childId") Long childId);
    FinancialProductEnrollmentVO selectDepositEnrollmentByChildIdAndId(
            @Param("childId") Long childId,
            @Param("enrollmentId") Long enrollmentId);
    FinancialProductEnrollmentVO selectSavingEnrollmentByChildIdAndId(
            @Param("childId") Long childId,
            @Param("enrollmentId") Long enrollmentId);
    FinancialProductEnrollmentVO selectLoanEnrollmentByChildIdAndId(
            @Param("childId") Long childId,
            @Param("enrollmentId") Long enrollmentId);
    FinancialProductBenefitVO selectBenefitByChildId(
            @Param("childId") Long childId);
    int countPendingDepositEnrollment(@Param("childId") Long childId,
                                      @Param("productId") Long productId);
    int countPendingSavingEnrollment(@Param("childId") Long childId,
                                     @Param("productId") Long productId);
    int countPendingLoanEnrollment(@Param("childId") Long childId,
                                   @Param("productId") Long productId);
    int insertDepositEnrollment(FinancialProductEnrollmentCommandVO command);
    int insertSavingEnrollment(FinancialProductEnrollmentCommandVO command);
    int insertLoanEnrollment(FinancialProductEnrollmentCommandVO command);
    List<FinancialProductApprovalVO> selectPendingApprovalsByParentId(
            @Param("parentId") Long parentId);
    FinancialProductApprovalVO selectDepositApprovalForUpdate(
            @Param("parentId") Long parentId,
            @Param("enrollmentId") Long enrollmentId);
    FinancialProductApprovalVO selectSavingApprovalForUpdate(
            @Param("parentId") Long parentId,
            @Param("enrollmentId") Long enrollmentId);
    FinancialProductApprovalVO selectLoanApprovalForUpdate(
            @Param("parentId") Long parentId,
            @Param("enrollmentId") Long enrollmentId);
    int approveDepositEnrollment(@Param("enrollmentId") Long enrollmentId,
                                 @Param("walletId") Long walletId,
                                 @Param("appliedRate") java.math.BigDecimal appliedRate,
                                 @Param("earlyTerminationRate") java.math.BigDecimal earlyTerminationRate,
                                 @Param("startDate") java.time.LocalDate startDate,
                                 @Param("maturityDate") java.time.LocalDate maturityDate);
    int approveSavingEnrollment(@Param("enrollmentId") Long enrollmentId,
                                @Param("walletId") Long walletId,
                                @Param("appliedRate") java.math.BigDecimal appliedRate,
                                @Param("earlyTerminationRate") java.math.BigDecimal earlyTerminationRate,
                                @Param("startDate") java.time.LocalDate startDate,
                                @Param("maturityDate") java.time.LocalDate maturityDate);
    int approveLoanEnrollment(@Param("enrollmentId") Long enrollmentId,
                              @Param("appliedRate") java.math.BigDecimal appliedRate,
                              @Param("lateFeeRate") java.math.BigDecimal lateFeeRate,
                              @Param("startDate") java.time.LocalDate startDate,
                              @Param("maturityDate") java.time.LocalDate maturityDate);
    int rejectDepositEnrollment(@Param("enrollmentId") Long enrollmentId);
    int rejectSavingEnrollment(@Param("enrollmentId") Long enrollmentId);
    int rejectLoanEnrollment(@Param("enrollmentId") Long enrollmentId);
    int insertFirstSavingPayment(@Param("enrollmentId") Long enrollmentId,
                                 @Param("transferId") Long transferId,
                                 @Param("amount") Long amount);
    List<Long> selectDueSavingPaymentEnrollmentIds(
            @Param("paymentDate") LocalDate paymentDate);
    com.teenyfin.teenymoney.domain.financialproduct.vo.SavingPaymentDueVO
    selectDueSavingPaymentForUpdate(
            @Param("enrollmentId") Long enrollmentId,
            @Param("paymentDate") LocalDate paymentDate);
    int countSavingPaymentHistory(@Param("enrollmentId") Long enrollmentId,
                                  @Param("installmentNo") Integer installmentNo);
    int insertSavingPaymentHistory(@Param("enrollmentId") Long enrollmentId,
                                   @Param("transferId") Long transferId,
                                   @Param("installmentNo") Integer installmentNo,
                                   @Param("amount") Long amount,
                                   @Param("paidAmount") Long paidAmount,
                                   @Param("status") String status);
    int upsertDepositProduct(DepositProductVO product);
    int upsertSavingProduct(SavingProductVO product);
    // 현재 공시 상품만 다시 활성화할 수 있도록 기존 FINLIFE 예금을 먼저 비활성화한다.
    int deactivateAllFinlifeDepositProducts();
    // 현재 공시 상품만 다시 활성화할 수 있도록 기존 FINLIFE 적금을 먼저 비활성화한다.
    int deactivateAllFinlifeSavingProducts();
    // 자유적금 가입 행을 잠가 동시 납입 시 월 한도를 직렬화한다.
    FreeSavingPaymentVO selectFreeSavingForPaymentForUpdate(
            @Param("childId") Long childId,
            @Param("enrollmentId") Long enrollmentId);
    FreeSavingPaymentVO selectFreeSavingPaymentByIdempotencyKey(
            @Param("enrollmentId") Long enrollmentId,
            @Param("idempotencyKey") String idempotencyKey);
    long selectFreeSavingPaidAmountInMonth(
            @Param("enrollmentId") Long enrollmentId,
            @Param("paymentDate") LocalDate paymentDate);
    int insertFreeSavingPayment(@Param("enrollmentId") Long enrollmentId,
                                @Param("transferId") Long transferId,
                                @Param("installmentNo") Integer installmentNo,
                                @Param("amount") Long amount);

    List<Long> selectDueDepositMaturityIds(@Param("processingDate") LocalDate processingDate);
    List<Long> selectDueSavingMaturityIds(@Param("processingDate") LocalDate processingDate);
    FinancialProductMaturityVO selectDepositMaturityForUpdate(
            @Param("enrollmentId") Long enrollmentId,
            @Param("processingDate") LocalDate processingDate);
    FinancialProductMaturityVO selectSavingMaturityForUpdate(
            @Param("enrollmentId") Long enrollmentId,
            @Param("processingDate") LocalDate processingDate);
    List<SavingContributionVO> selectSavingContributions(
            @Param("enrollmentId") Long enrollmentId);
    Integer selectFirstMissedSavingInstallment(@Param("enrollmentId") Long enrollmentId);
    int markDepositMatured(@Param("enrollmentId") Long enrollmentId);
    int markSavingMatured(@Param("enrollmentId") Long enrollmentId);

    List<Long> selectFreeSavingMonthlyTargetIds(
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEndExclusive") LocalDate monthEndExclusive);
    FreeSavingMonthlyVO selectFreeSavingMonthlyForUpdate(
            @Param("enrollmentId") Long enrollmentId,
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEndExclusive") LocalDate monthEndExclusive);
    long selectSavingPaidAmountBetween(
            @Param("enrollmentId") Long enrollmentId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    FinancialProductTerminationVO selectDepositTermination(
            @Param("childId") Long childId, @Param("enrollmentId") Long enrollmentId);
    FinancialProductTerminationVO selectSavingTermination(
            @Param("childId") Long childId, @Param("enrollmentId") Long enrollmentId);
    FinancialProductTerminationVO selectDepositTerminationForUpdate(
            @Param("childId") Long childId, @Param("enrollmentId") Long enrollmentId);
    FinancialProductTerminationVO selectSavingTerminationForUpdate(
            @Param("childId") Long childId, @Param("enrollmentId") Long enrollmentId);
    int markDepositTerminated(@Param("enrollmentId") Long enrollmentId);
    int markSavingTerminated(@Param("enrollmentId") Long enrollmentId);

    List<Long> selectLoanRepaymentTargetIds(@Param("processingDate") LocalDate processingDate);
    LoanRepaymentVO selectLoanRepaymentForUpdate(@Param("enrollmentId") Long enrollmentId);
    LoanRepaymentVO selectLoanRepaymentForRead(
            @Param("childId") Long childId, @Param("enrollmentId") Long enrollmentId);
    LoanRepaymentVO selectLoanRepaymentForChildForUpdate(
            @Param("childId") Long childId, @Param("enrollmentId") Long enrollmentId);
    LoanEarlyRepaymentHistoryVO selectLoanEarlyRepaymentByIdempotencyKey(
            @Param("enrollmentId") Long enrollmentId,
            @Param("idempotencyKey") String idempotencyKey);
    int countLoanRepaymentHistory(@Param("enrollmentId") Long enrollmentId,
                                  @Param("installmentNo") Integer installmentNo);
    int countLoanRepaymentHistoryOnDate(
            @Param("enrollmentId") Long enrollmentId,
            @Param("installmentNo") Integer installmentNo,
            @Param("attemptDate") LocalDate attemptDate);
    LocalDate selectLastLoanRepaymentAttemptDate(
            @Param("enrollmentId") Long enrollmentId,
            @Param("installmentNo") Integer installmentNo);
    int insertLoanRepaymentHistory(
            @Param("enrollmentId") Long enrollmentId,
            @Param("transferId") Long transferId,
            @Param("installmentNo") Integer installmentNo,
            @Param("repaymentType") String repaymentType,
            @Param("principalAmount") Long principalAmount,
            @Param("paidPrincipalAmount") Long paidPrincipalAmount,
            @Param("interestAmount") Long interestAmount,
            @Param("paidInterestAmount") Long paidInterestAmount,
            @Param("status") String status,
            @Param("dueDate") LocalDate dueDate);
    int updateLoanAfterRepayment(
            @Param("enrollmentId") Long enrollmentId,
            @Param("outstandingPrincipal") Long outstandingPrincipal,
            @Param("overdueInterest") Long overdueInterest,
            @Param("paidCount") Integer paidCount,
            @Param("status") String status);
}
