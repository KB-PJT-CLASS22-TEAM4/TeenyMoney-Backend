package com.teenyfin.teenymoney.domain.financialproduct.mapper;

import com.teenyfin.teenymoney.domain.financialproduct.vo.DepositProductVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductBenefitVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductEnrollmentVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductEnrollmentCommandVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductApprovalVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.LoanProductVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.SavingProductVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.time.LocalDate;

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
                                 @Param("appliedRate") java.math.BigDecimal appliedRate,
                                 @Param("earlyTerminationRate") java.math.BigDecimal earlyTerminationRate,
                                 @Param("startDate") java.time.LocalDate startDate,
                                 @Param("maturityDate") java.time.LocalDate maturityDate);
    int approveSavingEnrollment(@Param("enrollmentId") Long enrollmentId,
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
}
