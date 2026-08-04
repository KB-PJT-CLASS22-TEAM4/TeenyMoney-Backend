package com.teenyfin.teenymoney.domain.categoryPolicy.controller;

import com.teenyfin.teenymoney.domain.categoryPolicy.dto.request.CategoryPolicyUpdateRequestDTO;
import com.teenyfin.teenymoney.domain.categoryPolicy.dto.response.CategoryPolicyResponseDTO;
import com.teenyfin.teenymoney.domain.categoryPolicy.service.CategoryPolicyService;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/policies")
@RequiredArgsConstructor
public class CategoryPolicyController {

    private final CategoryPolicyService categoryPolicyService;

    @GetMapping
    public ApiResponse<List<CategoryPolicyResponseDTO>> getCategoryPolicy(@AuthenticationPrincipal MemberPrincipal memberPrincipal) {
        return ApiResponse.ok(categoryPolicyService.getCategoryPolicy(memberPrincipal.memberId(), memberPrincipal.role()));
    }

    @PatchMapping
    public ApiResponse<List<CategoryPolicyResponseDTO>> modifyCategoryPolicy(@AuthenticationPrincipal MemberPrincipal memberPrincipal, @RequestBody List<CategoryPolicyUpdateRequestDTO> categoryPolicyUpdateRequestDTOList) {
        return ApiResponse.ok(categoryPolicyService.updateCategoryPolicy(memberPrincipal.memberId(), memberPrincipal.role(), categoryPolicyUpdateRequestDTOList));
    }
}
