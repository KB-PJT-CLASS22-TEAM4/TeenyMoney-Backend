package com.teenyfin.teenymoney.domain.categoryPolicy.service;

import com.teenyfin.teenymoney.domain.categoryPolicy.dto.request.CategoryPolicyUpdateRequestDTO;
import com.teenyfin.teenymoney.domain.categoryPolicy.dto.response.CategoryPolicyResponseDTO;
import com.teenyfin.teenymoney.domain.categoryPolicy.exception.CategoryPolicyErrorCode;
import com.teenyfin.teenymoney.domain.categoryPolicy.mapper.CategoryPolicyMapper;
import com.teenyfin.teenymoney.domain.categoryPolicy.vo.CategoryPolicyVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryPolicyService {

    private final CategoryPolicyMapper categoryPolicyMapper;

    // 전체 카테고리 정책 조회
    @Transactional(readOnly = true)
    public List<CategoryPolicyResponseDTO> getCategoryPolicy(Long memberId, String role) {

        List<CategoryPolicyVO> categoryPolicyVOList = switch (role) {
            case "PARENT" -> categoryPolicyMapper.selectByParentId(memberId);
            case "CHILD" -> categoryPolicyMapper.selectByChildId(memberId);
            default -> throw new BusinessException(CategoryPolicyErrorCode.INVALID_ROLE); // 추후 MemberErrorCode 추가 시 변경
        };

        return categoryPolicyVOList.stream()
                .map(x -> CategoryPolicyResponseDTO.builder()
                        .id(x.getId())
                        .merchantCategoryName(x.getMerchantCategoryName())
                        .policy(x.getPolicy())
                        .build())
                .toList();
    }

    // 전체 카테고리 정책 일괄 수정
    @Transactional
    public List<CategoryPolicyResponseDTO> updateCategoryPolicy(Long memberId, String role, List<CategoryPolicyUpdateRequestDTO> categoryPolicyList) {

        // 자녀는 수정 권한 없음
        if (role.equals("CHILD")) {
            throw new BusinessException(CategoryPolicyErrorCode.CHILD_CAN_NOT_UPDATE_CATEGORY_POLICY);
        }

        int affected = categoryPolicyMapper.updateAllPolicies(memberId, categoryPolicyList);

        // 일부 실패 시 전체 롤백
        if (affected != categoryPolicyList.size()) {
            throw new BusinessException(CategoryPolicyErrorCode.INVALID_CATEGORY_POLICY_ID);
        }

        List<CategoryPolicyVO> categoryPolicyVOList = categoryPolicyMapper.selectByParentId(memberId);

        return categoryPolicyVOList.stream()
                .map(x -> CategoryPolicyResponseDTO.builder()
                        .id(x.getId())
                        .merchantCategoryName(x.getMerchantCategoryName())
                        .policy(x.getPolicy())
                        .build())
                .toList();
    }
}
