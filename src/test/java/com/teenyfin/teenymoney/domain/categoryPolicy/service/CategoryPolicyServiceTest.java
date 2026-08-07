package com.teenyfin.teenymoney.domain.categoryPolicy.service;

import com.teenyfin.teenymoney.domain.categoryPolicy.dto.response.CategoryPolicyGroupResponseDTO;
import com.teenyfin.teenymoney.domain.categoryPolicy.dto.response.CategoryPolicyResponseDTO;
import com.teenyfin.teenymoney.domain.categoryPolicy.mapper.CategoryPolicyMapper;
import com.teenyfin.teenymoney.domain.categoryPolicy.vo.CategoryPolicyVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CategoryPolicyServiceTest {

    private final CategoryPolicyMapper categoryPolicyMapper = Mockito.mock(CategoryPolicyMapper.class);
    private final CategoryPolicyService categoryPolicyService = new CategoryPolicyService(categoryPolicyMapper);

    @Test
    void 자녀가_조회하면_본인_아이디로_selectByChildId를_호출한다() {
        // given
        Long memberId = 2L;
        String role = "CHILD";

        CategoryPolicyVO vo = CategoryPolicyVO.builder()
                .id(2L)
                .merchantCategoryName("PC방")
                .policy("BLOCK")
                .build();
        given(categoryPolicyMapper.selectByChildId(memberId)).willReturn(List.of(vo));

        // when
        List<CategoryPolicyResponseDTO> result = categoryPolicyService.getCategoryPolicy(memberId, role, null);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPolicy()).isEqualTo("BLOCK");
        verify(categoryPolicyMapper).selectByChildId(memberId);
    }

    @Test
    void 부모가_childId를_지정해서_조회하면_해당_자녀_기준으로_조회된다() {
        // given
        Long parentId = 1L;
        Long childId = 2L;
        String role = "PARENT";

        CategoryPolicyVO vo = CategoryPolicyVO.builder()
                .id(1L)
                .merchantCategoryName("외식")
                .policy("ALLOW")
                .build();

        given(categoryPolicyMapper.selectParentIdByChildId(childId)).willReturn(parentId);
        given(categoryPolicyMapper.selectByChildId(childId)).willReturn(List.of(vo));

        // when
        List<CategoryPolicyResponseDTO> result = categoryPolicyService.getCategoryPolicy(parentId, role, childId);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMerchantCategoryName()).isEqualTo("외식");
        verify(categoryPolicyMapper).selectByChildId(childId);
    }

    @Test
    void 부모가_childId를_지정하지_않으면_예외를_던진다() {
        // given
        Long parentId = 1L;
        String role = "PARENT";

        // when & then
        assertThatThrownBy(() -> categoryPolicyService.getCategoryPolicy(parentId, role, null))
                .isInstanceOf(BusinessException.class);

        verify(categoryPolicyMapper, never()).selectByChildId(any());
    }

    @Test
    void 부모가_본인_자녀가_아닌_아이디로_조회하면_예외를_던진다() {
        // given
        Long parentId = 1L;
        Long childId = 2L;
        String role = "PARENT";

        given(categoryPolicyMapper.selectParentIdByChildId(childId)).willReturn(999L); // 다른 부모의 자녀

        // when & then
        assertThatThrownBy(() -> categoryPolicyService.getCategoryPolicy(parentId, role, childId))
                .isInstanceOf(BusinessException.class);

        verify(categoryPolicyMapper, never()).selectByChildId(any());
    }

    @Test
    void 정책이_없으면_빈리스트를_반환한다() {
        // given
        Long memberId = 2L;
        String role = "CHILD";

        given(categoryPolicyMapper.selectByChildId(memberId)).willReturn(List.of());

        // when
        List<CategoryPolicyResponseDTO> result = categoryPolicyService.getCategoryPolicy(memberId, role, null);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void 정책_단계별로_그룹핑되고_순서는_ALLOW_WATCH_BLOCK_순이다() {
        // given
        Long memberId = 2L;
        String role = "CHILD";

        given(categoryPolicyMapper.selectByChildId(memberId)).willReturn(List.of(
                CategoryPolicyVO.builder().id(1L).merchantCategoryName("편의점").policy("ALLOW").build(),
                CategoryPolicyVO.builder().id(3L).merchantCategoryName("PC방").policy("WATCH").build(),
                CategoryPolicyVO.builder().id(5L).merchantCategoryName("유흥주점").policy("BLOCK").build()
        ));

        // when
        List<CategoryPolicyGroupResponseDTO> result =
                categoryPolicyService.getCategoryPolicyGroup(memberId, role, null);

        // then
        assertThat(result).extracting(CategoryPolicyGroupResponseDTO::getPolicy)
                .containsExactly("ALLOW", "WATCH", "BLOCK");
    }
}