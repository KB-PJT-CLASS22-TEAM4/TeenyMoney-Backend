package com.teenyfin.teenymoney.domain.teenyscore.service;

import com.teenyfin.teenymoney.domain.family.service.FamilyAccessService;
import com.teenyfin.teenymoney.domain.teenyscore.dto.response.TeenyScoreGradeResponseDTO;
import com.teenyfin.teenymoney.domain.teenyscore.dto.response.TeenyScoreHistoryResponseDTO;
import com.teenyfin.teenymoney.domain.teenyscore.dto.response.TeenyScoreMonthlyHistoryResponseDTO;
import com.teenyfin.teenymoney.domain.teenyscore.dto.response.TeenyScoreResponseDTO;
import com.teenyfin.teenymoney.domain.teenyscore.exception.TeenyScoreErrorCode;
import com.teenyfin.teenymoney.domain.teenyscore.mapper.TeenyScoreMapper;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreGradeVO;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.exception.CommonErrorCode;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TeenyScoreService {

    private final TeenyScoreMapper teenyScoreMapper;
    private final FamilyAccessService familyAccessService;

    public TeenyScoreService(
            TeenyScoreMapper teenyScoreMapper,
            FamilyAccessService familyAccessService) {
        this.teenyScoreMapper = teenyScoreMapper;
        this.familyAccessService = familyAccessService;
    }

    public TeenyScoreResponseDTO getTeenyScore(
            MemberPrincipal principal,
            Long childId) {
        familyAccessService.requireChildAccess(principal, childId);
        TeenyScoreVO teenyScore = findTeenyScore(childId);
        return TeenyScoreResponseDTO.of(teenyScore);
    }

    public List<TeenyScoreHistoryResponseDTO> getMyHistories(
            MemberPrincipal principal) {
        validateChild(principal);
        findTeenyScore(principal.memberId());

        return teenyScoreMapper.selectHistoriesByChildId(principal.memberId())
                .stream()
                .map(TeenyScoreHistoryResponseDTO::of)
                .toList();
    }

    public List<TeenyScoreMonthlyHistoryResponseDTO> getMonthlyHistories(
            MemberPrincipal principal,
            Long childId) {

        familyAccessService.requireChildAccess(principal, childId);
        findTeenyScore(childId);

        return teenyScoreMapper.selectMonthlyHistoriesByChildId(childId)
                .stream()
                .map(TeenyScoreMonthlyHistoryResponseDTO::of)
                .toList();
    }

    public List<TeenyScoreGradeResponseDTO> getGrades() {
        List<TeenyScoreGradeVO> grades = teenyScoreMapper.selectAllGrades();

        if (grades.isEmpty()) {
            throw new BusinessException(
                    TeenyScoreErrorCode.TEENY_SCORE_GRADE_NOT_FOUND);
        }

        return grades.stream()
                .map(TeenyScoreGradeResponseDTO::of)
                .toList();
    }

    private TeenyScoreVO findTeenyScore(Long childId) {
        TeenyScoreVO teenyScore =
                teenyScoreMapper.selectTeenyScoreByChildId(childId);

        if (teenyScore == null) {
            throw new BusinessException(
                    TeenyScoreErrorCode.TEENY_SCORE_CHILD_NOT_FOUND);
        }

        return teenyScore;
    }

    private void validateChild(MemberPrincipal principal) {
        if (principal == null || !"CHILD".equals(principal.role())) {
            throw new BusinessException(CommonErrorCode.AUTH_FORBIDDEN);
        }
    }

}
