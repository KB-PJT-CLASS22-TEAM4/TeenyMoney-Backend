package com.teenyfin.teenymoney.domain.teenyscore.mapper;

import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreEventRecordVO;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreGradeChangeVO;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreGradeVO;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreHistoryVO;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreMonthlyHistoryVO;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TeenyScoreMapper {
    TeenyScoreVO selectTeenyScoreByChildId(
            @Param("childId") Long childId);

    List<TeenyScoreHistoryVO> selectHistoriesByChildId(
            @Param("childId") Long childId);

    List<TeenyScoreMonthlyHistoryVO> selectMonthlyHistoriesByChildId(
            @Param("childId") Long childId);

    List<TeenyScoreGradeVO> selectAllGrades();

    @SuppressWarnings("MybatisXMapperMethodInspection")
    Integer selectScoreForUpdate(
            @Param("childId") Long childId);

    boolean existsHistoryByEventKey(
            @Param("childId") Long childId,
            @Param("eventKey") String eventKey);

    int updateTeenyScore(
            @Param("childId") Long childId,
            @Param("teenyScore") int teenyScore);

    /** 일괄 갱신 직전에 호출해, 이번 확정으로 등급이 실제로 바뀌는 자녀만 조회한다. */
    List<TeenyScoreGradeChangeVO> selectPendingGradeChanges();

    int updateAllActiveChildGrades(
            @Param("appliedAt") LocalDateTime appliedAt);

    int initializeAppliedGrade(
            @Param("childId") Long childId,
            @Param("appliedAt") LocalDateTime appliedAt);

    int insertScoreHistory(
            @Param("childId") Long childId,
            @Param("amount") int amount,
            @Param("scoreAfter") int scoreAfter,
            @Param("eventCode") String eventCode,
            @Param("eventKey") String eventKey,
            @Param("description") String description,
            @Param("referenceType") String referenceType,
            @Param("referenceId") Long referenceId);

    TeenyScoreGradeVO selectTeenyScoreGradeByChildId(@Param("childId") Long childId);

    /**
     * 연속 만기 보너스·반복 중도해지 감점 판정용. 만기·해지 이벤트 중 가장 최근 것부터
     * limit건을 최신순으로 조회한다. 기간 제한은 없다 — 몇 개월이 걸렸든 "연속"이면 대상이다.
     */
    List<TeenyScoreEventRecordVO> selectRecentFinalSavingEvents(
            @Param("childId") Long childId,
            @Param("limit") int limit);
}
