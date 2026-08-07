package com.teenyfin.teenymoney.domain.categoryPolicy.controller;

import com.teenyfin.teenymoney.domain.categoryPolicy.dto.request.CategoryPolicyUpdateRequestListDTO;
import com.teenyfin.teenymoney.domain.categoryPolicy.dto.response.CategoryPolicyGroupResponseDTO;
import com.teenyfin.teenymoney.domain.categoryPolicy.dto.response.CategoryPolicyResponseDTO;
import com.teenyfin.teenymoney.domain.categoryPolicy.service.CategoryPolicyService;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@Api(tags = "업종 카테고리 정책")
@RestController
@RequestMapping("/category-policies")
@RequiredArgsConstructor
public class CategoryPolicyController {

    private final CategoryPolicyService categoryPolicyService;

    @ApiOperation(value = "단계 별 카테고리 정책 조회", notes = "허용, 주의, 차단 단계로 설정된 카테고리들을 묶어 반환합니다.")
    @GetMapping("/groups")
    public ApiResponse<List<CategoryPolicyGroupResponseDTO>> getCategoryPolicyGroup(
            @AuthenticationPrincipal MemberPrincipal memberPrincipal,
            @RequestParam(required = false) Long childId) {
        return ApiResponse.ok(categoryPolicyService.getCategoryPolicyGroup(memberPrincipal.memberId(), memberPrincipal.role(), childId));
    }

    @ApiOperation(value = "전체 카테고리 정책 조회", notes = "모든 카테고리의 정보(ID, 카테고리 이름, 정책 단계)를 반환합니다.")
    @GetMapping
    public ApiResponse<List<CategoryPolicyResponseDTO>> getCategoryPolicy(
            @AuthenticationPrincipal MemberPrincipal memberPrincipal,
            @RequestParam(required = false) Long childId) {
        return ApiResponse.ok(categoryPolicyService.getCategoryPolicy(memberPrincipal.memberId(), memberPrincipal.role(), childId));
    }

    @ApiOperation(value = "전체 카테고리 정책 단계 수정", notes = "모든 카테고리의 정책 단계를 동시에 수정합니다.")
    @PatchMapping
    public ApiResponse<List<CategoryPolicyResponseDTO>> modifyCategoryPolicy(
            @AuthenticationPrincipal MemberPrincipal memberPrincipal,
            @RequestParam Long childId,
            @ApiParam(value = "전체 카테고리에 대한 ID와 변경 후 정책 단계", required = true) @RequestBody @Valid CategoryPolicyUpdateRequestListDTO categoryPolicyUpdateRequestDTOList) {
        return ApiResponse.ok(categoryPolicyService.updateCategoryPolicy(memberPrincipal.memberId(), memberPrincipal.role(), childId, categoryPolicyUpdateRequestDTOList.getCategoryPolicyList()));
    }
}
