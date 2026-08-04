package com.teenyfin.teenymoney.domain.categoryPolicy.service;

import com.teenyfin.teenymoney.domain.categoryPolicy.dto.request.CategoryPolicyUpdateRequestDTO;
import com.teenyfin.teenymoney.domain.categoryPolicy.dto.response.CategoryPolicyResponseDTO;
import com.teenyfin.teenymoney.domain.categoryPolicy.mapper.CategoryPolicyMapper;
import com.teenyfin.teenymoney.domain.categoryPolicy.vo.CategoryPolicyVO;
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
            // exception throw 커스텀하기
            default -> throw new IllegalStateException();
        };

        return categoryPolicyVOList.stream()
                .map(x -> CategoryPolicyResponseDTO.builder()
                        .id(x.getId())
                        .merchantCategoryName(x.getMerchantCategoryName())
                        .policy(x.getPolicy())
                        .build())
                .toList();
    }

    // 전체 카테고리 정책 수정
    @Transactional
    public List<CategoryPolicyResponseDTO> updateCategoryPolicy(Long memberId, String role, List<CategoryPolicyUpdateRequestDTO> categoryPolicyList) {
        if (role.equals("CHILD")) {
            // 수정 권한 없음
            throw new IllegalStateException("자녀는 정책 수정에 대한 권한이 없습니다.");
        }

        int affected = categoryPolicyMapper.updateAllPolicies(memberId, categoryPolicyList);

        // 예외 던진 후 전체 롤백
        if (affected != categoryPolicyList.size()) {
            throw new IllegalStateException("일부 정책이 본인 소유가 아니거나 존재하지 않습니다.");
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
