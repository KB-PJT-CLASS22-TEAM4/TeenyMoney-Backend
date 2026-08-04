package com.teenyfin.teenymoney.domain.teenyscore.mapper;

import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreGradeVO;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreHistoryVO;
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

    List<TeenyScoreGradeVO> selectAllGrades();
}
