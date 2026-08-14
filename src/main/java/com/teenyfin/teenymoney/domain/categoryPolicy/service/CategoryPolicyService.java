package com.teenyfin.teenymoney.domain.categoryPolicy.service;

import com.teenyfin.teenymoney.domain.categoryPolicy.dto.request.CategoryPolicyUpdateRequestDTO;
import com.teenyfin.teenymoney.domain.categoryPolicy.dto.response.CategoryPolicyGroupResponseDTO;
import com.teenyfin.teenymoney.domain.categoryPolicy.dto.response.CategoryPolicyResponseDTO;
import com.teenyfin.teenymoney.domain.categoryPolicy.exception.CategoryPolicyErrorCode;
import com.teenyfin.teenymoney.domain.categoryPolicy.mapper.CategoryPolicyMapper;
import com.teenyfin.teenymoney.domain.categoryPolicy.vo.CategoryPolicy;
import com.teenyfin.teenymoney.domain.categoryPolicy.vo.CategoryPolicyVO;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryPolicyService {

    private final CategoryPolicyMapper categoryPolicyMapper;
    private final MemberMapper memberMapper;

    private static final List<CategoryPolicy> POLICY_ORDER = List.of(CategoryPolicy.ALLOW, CategoryPolicy.WATCH, CategoryPolicy.BLOCK);

    // 단계 별 카테고리 정책 조회
    @Transactional(readOnly = true)
    public List<CategoryPolicyGroupResponseDTO> getCategoryPolicyGroup(Long memberId, String role, Long childId) {

        List<CategoryPolicyResponseDTO> categoryPolicyResponseDTOList = getCategoryPolicy(memberId, role, childId);

        Map<CategoryPolicy, List<CategoryPolicyResponseDTO>> grouped = categoryPolicyResponseDTOList.stream()
                .collect(Collectors.groupingBy(
                        CategoryPolicyResponseDTO::getPolicy,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return POLICY_ORDER.stream()
                .map(policy -> CategoryPolicyGroupResponseDTO.builder()
                        .policy(policy)
                        .categoryPolicyList(grouped.getOrDefault(policy, List.of()))
                        .build())
                .toList();
    }

    // 전체 카테고리 정책 조회
    @Transactional(readOnly = true)
    public List<CategoryPolicyResponseDTO> getCategoryPolicy(Long memberId, String role, Long childId) {

        if (role.equals("CHILD")) {
            childId = memberId;
        } else if (childId == null) {   // 부모의 경우 childId 값은 필수
            throw new BusinessException(CategoryPolicyErrorCode.CHILD_ID_REQUIRED);
        } else if (!Objects.equals(memberMapper.selectActiveParentByChildId(childId).getParentId(), memberId)) {  // 해당 자녀와 연결된 부모인지 확인
            throw new BusinessException(CategoryPolicyErrorCode.FORBIDDEN_TO_CHILD);
        }

        List<CategoryPolicyVO> categoryPolicyVOList =  categoryPolicyMapper.selectByChildId(childId);

        return categoryPolicyVOList.stream()
                .map(x -> CategoryPolicyResponseDTO.builder()
                        .id(x.getId())
                        .categoryName(x.getCategoryName())
                        .policy(x.getPolicy())
                        .build())
                .toList();
    }

    // 전체 카테고리 정책 단계 수정
    @Transactional
    public List<CategoryPolicyResponseDTO> updateCategoryPolicy(Long memberId, String role, Long childId, List<CategoryPolicyUpdateRequestDTO> categoryPolicyList) {

        // 자녀는 수정 권한 없음
        if (role.equals("CHILD")) {
            throw new BusinessException(CategoryPolicyErrorCode.CHILD_CAN_NOT_UPDATE_CATEGORY_POLICY);
        } else if (childId == null) {   // 부모의 경우 childId 값은 필수
            throw new BusinessException(CategoryPolicyErrorCode.CHILD_ID_REQUIRED);
        } else if (!Objects.equals(memberMapper.selectActiveParentByChildId(childId).getParentId(), memberId)) {  // 해당 자녀와 연결된 부모인지 확인
            throw new BusinessException(CategoryPolicyErrorCode.FORBIDDEN_TO_CHILD);
        }

        int resultCount = categoryPolicyMapper.updateAllPolicies(memberId, childId, categoryPolicyList);

        // 일부 실패 시 전체 롤백
        if (resultCount != categoryPolicyList.size()) {
            throw new BusinessException(CategoryPolicyErrorCode.INVALID_CATEGORY_POLICY_ID);
        }

        List<CategoryPolicyVO> categoryPolicyVOList = categoryPolicyMapper.selectByChildId(childId);

        return categoryPolicyVOList.stream()
                .map(x -> CategoryPolicyResponseDTO.builder()
                        .id(x.getId())
                        .categoryName(x.getCategoryName())
                        .policy(x.getPolicy())
                        .build())
                .toList();
    }
}
