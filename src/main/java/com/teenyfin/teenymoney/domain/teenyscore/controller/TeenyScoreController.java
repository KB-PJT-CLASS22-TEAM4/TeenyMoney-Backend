package com.teenyfin.teenymoney.domain.teenyscore.controller;

import com.teenyfin.teenymoney.domain.teenyscore.dto.response.TeenyScoreGradeResponseDTO;
import com.teenyfin.teenymoney.domain.teenyscore.dto.response.TeenyScoreHistoryResponseDTO;
import com.teenyfin.teenymoney.domain.teenyscore.dto.response.TeenyScoreResponseDTO;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScoreService;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/teeny-score")
public class TeenyScoreController {

    private final TeenyScoreService teenyScoreService;

    public TeenyScoreController(TeenyScoreService teenyScoreService) {
        this.teenyScoreService = teenyScoreService;
    }

    @GetMapping("/children/{childId}")
    public ApiResponse<TeenyScoreResponseDTO> getTeenyScore(
            @PathVariable Long childId) {
        return ApiResponse.ok(
                teenyScoreService.getTeenyScore(childId));
    }

    @GetMapping("/children/{childId}/history")
    public ApiResponse<List<TeenyScoreHistoryResponseDTO>> getHistories(
            @PathVariable Long childId) {
        return ApiResponse.ok(
                teenyScoreService.getHistories(childId));
    }

    @GetMapping("/grades")
    public ApiResponse<List<TeenyScoreGradeResponseDTO>> getGrades() {
        return ApiResponse.ok(
                teenyScoreService.getGrades());
    }
}
