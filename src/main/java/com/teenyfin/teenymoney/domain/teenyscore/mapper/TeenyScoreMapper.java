package com.teenyfin.teenymoney.domain.teenyscore.mapper;

import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreGradeVO;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreHistoryVO;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreMonthlyHistoryVO;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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

    int insertScoreHistory(
            @Param("childId") Long childId,
            @Param("amount") int amount,
            @Param("scoreAfter") int scoreAfter,
            @Param("eventCode") String eventCode,
            @Param("eventKey") String eventKey,
            @Param("description") String description,
            @Param("referenceType") String referenceType,
            @Param("referenceId") Long referenceId);
}
