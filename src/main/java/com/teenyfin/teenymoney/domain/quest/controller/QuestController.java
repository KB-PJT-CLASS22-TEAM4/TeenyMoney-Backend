package com.teenyfin.teenymoney.domain.quest.controller;

import com.teenyfin.teenymoney.domain.quest.dto.request.QuestCreateRequestDTO;
import com.teenyfin.teenymoney.domain.quest.dto.request.QuestDeclineRequestDTO;
import com.teenyfin.teenymoney.domain.quest.dto.request.QuestUpdateRequestDTO;
import com.teenyfin.teenymoney.domain.quest.dto.response.QuestDetailResponseDTO;
import com.teenyfin.teenymoney.domain.quest.dto.response.QuestListResponseDTO;
import com.teenyfin.teenymoney.domain.quest.service.QuestCreationService;
import com.teenyfin.teenymoney.domain.quest.service.QuestProgressService;
import com.teenyfin.teenymoney.domain.quest.service.QuestQueryService;
import com.teenyfin.teenymoney.domain.quest.vo.QuestTab;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@Api(tags = "퀘스트", description = "퀘스트 생성·조회·수정·삭제·수락·거절 API")
@RestController
@RequestMapping("/quests")
@RequiredArgsConstructor
public class QuestController {

    private final QuestProgressService questProgressService;
    private final QuestCreationService questCreationService;
    private final QuestQueryService questQueryService;

    @ApiOperation(
            value = "퀘스트 목록 조회",
            notes = "부모와 자녀가 같은 응답 형식을 사용하며, 로그인한 역할에 맞는 퀘스트만 조회합니다.")
    @GetMapping
    public ApiResponse<QuestListResponseDTO> getQuests(
            @AuthenticationPrincipal MemberPrincipal principal,
            @ApiParam(value = "AVAILABLE, ONGOING, COMPLETED", required = true)
            @RequestParam QuestTab tab,
            @ApiParam(value = "부모만 사용할 수 있는 자녀 필터")
            @RequestParam(required = false) Long childId,
            @ApiParam(value = "직전 응답의 nextCursor")
            @RequestParam(required = false) String cursor) {
        return ApiResponse.ok(questQueryService.getQuests(principal, tab, childId, cursor));
    }

    @ApiOperation(
            value = "퀘스트 상세 조회",
            notes = "다른 가족의 퀘스트와 존재하지 않는 퀘스트는 같은 404 오류로 응답합니다.")
    @GetMapping("/{questId}")
    public ApiResponse<QuestDetailResponseDTO> getQuest(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long questId) {
        return ApiResponse.ok(questQueryService.getQuest(principal, questId));
    }

    @ApiOperation(
            value = "퀘스트 일괄 생성",
            notes = "선택한 자녀마다 독립된 퀘스트를 생성하고 요청한 자녀 순서대로 ID를 반환합니다.")
    @PreAuthorize("hasRole('PARENT')")
    @PostMapping
    public ApiResponse<List<Long>> createQuests(
            @AuthenticationPrincipal MemberPrincipal principal,
            @ApiParam(value = "생성 요청 식별용 UUID", required = true)
            @RequestHeader("X-Creation-Request-Key") String creationRequestKey,
            @RequestBody @Valid QuestCreateRequestDTO request) {
        return ApiResponse.ok(questCreationService.create(principal, request, creationRequestKey));
    }

    @ApiOperation(
            value = "시작 전 퀘스트 수정",
            notes = "AVAILABLE 상태이며 마감되지 않은 부모 본인의 퀘스트만 수정합니다.")
    @PreAuthorize("hasRole('PARENT')")
    @PatchMapping("/{questId}")
    public ApiResponse<QuestDetailResponseDTO> updateQuest(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long questId,
            @RequestBody @Valid QuestUpdateRequestDTO request) {
        questCreationService.update(principal, questId, request);
        return ApiResponse.ok(questQueryService.getQuest(principal, questId));
    }

    @ApiOperation(
            value = "시작 전 퀘스트 삭제",
            notes = "AVAILABLE 상태이며 마감되지 않은 부모 본인의 퀘스트를 실제 데이터에서도 삭제합니다.")
    @PreAuthorize("hasRole('PARENT')")
    @DeleteMapping("/{questId}")
    public ApiResponse<Void> deleteQuest(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long questId) {
        questCreationService.delete(principal, questId);
        return ApiResponse.ok();
    }

    @ApiOperation(
            value = "퀘스트 수락",
            notes = "AVAILABLE 상태이며 기한이 지나지 않은 본인 퀘스트를 IN_PROGRESS로 바꿉니다.")
    @PreAuthorize("hasRole('CHILD')")
    @PatchMapping("/{questId}/accept")
    public ApiResponse<QuestDetailResponseDTO> acceptQuest(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long questId) {
        questProgressService.accept(principal, questId);
        return ApiResponse.ok(questQueryService.getQuest(principal, questId));
    }

    @ApiOperation(
            value = "퀘스트 거절",
            notes = "AVAILABLE 상태인 본인 퀘스트를 사유와 함께 DECLINED로 바꿉니다. 티니점수는 차감하지 않습니다.")
    @PreAuthorize("hasRole('CHILD')")
    @PatchMapping("/{questId}/decline")
    public ApiResponse<QuestDetailResponseDTO> declineQuest(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long questId,
            @RequestBody @Valid QuestDeclineRequestDTO request) {
        questProgressService.decline(principal, questId, request);
        return ApiResponse.ok(questQueryService.getQuest(principal, questId));
    }
}
