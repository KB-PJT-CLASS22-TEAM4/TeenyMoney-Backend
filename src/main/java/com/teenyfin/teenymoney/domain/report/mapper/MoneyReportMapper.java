package com.teenyfin.teenymoney.domain.report.mapper;

import com.teenyfin.teenymoney.domain.report.vo.ChildProfileVO;
import com.teenyfin.teenymoney.domain.report.vo.DailySpendingVO;
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
}
