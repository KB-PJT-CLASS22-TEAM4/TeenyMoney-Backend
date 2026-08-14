package com.teenyfin.teenymoney.domain.report.mapper;

import com.teenyfin.teenymoney.domain.report.vo.ChildProfileVO;
import com.teenyfin.teenymoney.domain.report.vo.ClosingProductVO;
import com.teenyfin.teenymoney.domain.report.vo.DailySpendingVO;
import com.teenyfin.teenymoney.domain.report.vo.LoanOverdueVO;
import com.teenyfin.teenymoney.domain.report.vo.LoanRepaymentVO;
import com.teenyfin.teenymoney.domain.report.vo.MoneyFlowVO;
import com.teenyfin.teenymoney.domain.report.vo.PermissionSummaryVO;
import com.teenyfin.teenymoney.domain.report.vo.ProductPeriodVO;
import com.teenyfin.teenymoney.domain.report.vo.QuestSummaryVO;
import com.teenyfin.teenymoney.domain.report.vo.ScoreHistoryVO;
import com.teenyfin.teenymoney.domain.report.vo.SpendingCategoryVO;
import com.teenyfin.teenymoney.domain.report.vo.SpendingTotalVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 머니 리포트 전용 집계.
 *
 * 리포트는 여러 도메인의 테이블을 읽지만 각 도메인 mapper에 조회용 쿼리를 흩뿌리지 않고
 * 여기 모은다. 읽기 전용이라 다른 도메인 테이블을 봐도 실질 위험이 낮고,
 * "이 화면의 숫자가 어디서 나오는가"가 한 파일에 모인다.
 *
 * 기간 파라미터는 모두 [from, to] 양끝 포함이다. created_at이 DATETIME이므로
 * XML에서 to의 다음 날 자정 미만으로 비교해 마지막 날이 통째로 들어가게 한다.
 */
@Mapper
public interface MoneyReportMapper {

    // 연령 모드(birth_date)와 가입 월(created_at)에 쓴다
    ChildProfileVO selectChildProfile(@Param("childId") Long childId);

    // 기간 내 성공 결제의 총액과 건수
    SpendingTotalVO selectSpendingTotal(
            @Param("childId") Long childId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    // 기간 내 성공 결제를 카테고리 × 적용정책으로 집계
    List<SpendingCategoryVO> selectSpendingByCategory(
            @Param("childId") Long childId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    // 기간 내 성공 결제의 일자별 합계 (주차 버킷팅은 Java에서 한다)
    List<DailySpendingVO> selectDailySpending(
            @Param("childId") Long childId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    // 기간 내 예금·적금 이동과 퀘스트 보상 수령 (모은 돈 / 직접 얻은 돈)
    MoneyFlowVO selectMoneyFlow(
            @Param("childId") Long childId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    // 기간 내 실제 납부한 대출 원금과 이자 (갚은 돈)
    LoanRepaymentVO selectLoanRepayment(
            @Param("childId") Long childId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    // 지금 연체 중인 대출. 기간을 받지 않는다 — 진행 중인 달에만 부른다.
    LoanOverdueVO selectLoanOverdue(@Param("childId") Long childId);

    // 기간 내 오늘만 허용 요청의 상태별 건수
    PermissionSummaryVO selectPermissionSummary(
            @Param("childId") Long childId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    // 기간 내 종료된 퀘스트와 진행 중인 퀘스트
    QuestSummaryVO selectQuestSummary(
            @Param("childId") Long childId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    // 그 달에 만기·종료되는 예금·적금. monthEnd 는 조회 종료일이 아니라 그 달 말일이다.
    List<ClosingProductVO> selectClosingProducts(
            @Param("childId") Long childId,
            @Param("from") LocalDate from,
            @Param("monthEnd") LocalDate monthEnd);

    // 기간 내 티니점수 변동 이력
    List<ScoreHistoryVO> selectScoreHistory(
            @Param("childId") Long childId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    // 가입 상품이 살아 있던 기간. 활동 월 판정에 함께 쓴다.
    List<ProductPeriodVO> selectProductPeriods(@Param("childId") Long childId);

    // 활동이 하나라도 있었던 달 (yyyy-MM). 월 선택의 '기록 없음' 판정에 쓴다.
    List<String> selectActivityMonths(@Param("childId") Long childId);
}
