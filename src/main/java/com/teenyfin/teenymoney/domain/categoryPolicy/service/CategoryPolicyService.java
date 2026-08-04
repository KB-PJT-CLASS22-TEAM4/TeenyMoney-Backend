package com.teenyfin.teenymoney.domain.categoryPolicy.service;

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

    @Transactional(readOnly = true)
    public List<CategoryPolicyResponseDTO> getCategoryPolicy(MemberPrincipal memberPrincipal) {
        Long memberId = memberPrincipal.memberId();
        String role = memberPrincipal.role();

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
}
