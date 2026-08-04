package com.teenyfin.teenymoney.domain.categoryPolicy.service;

import com.teenyfin.teenymoney.domain.categoryPolicy.dto.response.CategoryPolicyGroupResponseDTO;
import com.teenyfin.teenymoney.domain.categoryPolicy.dto.response.CategoryPolicyResponseDTO;
import com.teenyfin.teenymoney.domain.categoryPolicy.mapper.CategoryPolicyMapper;
import com.teenyfin.teenymoney.domain.categoryPolicy.vo.CategoryPolicyVO;
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
    void 부모가_조회하면_selectByParentId를_호출한다() {
        // given
        Long memberId = 1L;
        String role = "PARENT";

        CategoryPolicyVO vo = CategoryPolicyVO.builder()
                .id(1L)
                .merchantCategoryName("외식")
                .policy("ALLOW")
                .build();
        given(categoryPolicyMapper.selectByParentId(1L)).willReturn(List.of(vo));

        // when
        List<CategoryPolicyResponseDTO> result = categoryPolicyService.getCategoryPolicy(memberId, role);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMerchantCategoryName()).isEqualTo("외식");
        assertThat(result.get(0).getPolicy()).isEqualTo("ALLOW");
        verify(categoryPolicyMapper).selectByParentId(1L);
        verify(categoryPolicyMapper, never()).selectByChildId(any());
    }

    @Test
    void 자녀가_조회하면_selectByChildId를_호출한다() {
        // given
        Long memberId = 2L;
        String role = "CHILD";

        CategoryPolicyVO vo = CategoryPolicyVO.builder()
                .id(2L)
                .merchantCategoryName("PC방")
                .policy("BLOCK")
                .build();
        given(categoryPolicyMapper.selectByChildId(2L)).willReturn(List.of(vo));

        // when
        List<CategoryPolicyResponseDTO> result = categoryPolicyService.getCategoryPolicy(memberId, role);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPolicy()).isEqualTo("BLOCK");
        verify(categoryPolicyMapper).selectByChildId(2L);
        verify(categoryPolicyMapper, never()).selectByParentId(any());
    }

    @Test
    void 정책이_없으면_빈리스트를_반환한다() {
        // given
        Long memberId = 1L;
        String role = "PARENT";

        given(categoryPolicyMapper.selectByParentId(1L)).willReturn(List.of());

        // when
        List<CategoryPolicyResponseDTO> result = categoryPolicyService.getCategoryPolicy(memberId, role);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void 알수없는_role이면_예외를_던진다() {
        // given
        Long memberId = 1L;
        String role = "UNKNOWN";

        // when & then
        assertThatThrownBy(() -> categoryPolicyService.getCategoryPolicy(memberId, role))
                .isInstanceOf(IllegalStateException.class);

        verify(categoryPolicyMapper, never()).selectByParentId(any());
        verify(categoryPolicyMapper, never()).selectByChildId(any());
    }

    @Test
    void 정책_단계별로_그룹핑되고_순서는_ALLOW_WATCH_BLOCK_순이다() {
        // given
        Long memberId = 1L;
        String role = "PARENT";

        given(categoryPolicyMapper.selectByParentId(1L)).willReturn(List.of(
                CategoryPolicyVO.builder().id(1L).merchantCategoryName("편의점").policy("ALLOW").build(),
                CategoryPolicyVO.builder().id(3L).merchantCategoryName("PC방").policy("WATCH").build(),
                CategoryPolicyVO.builder().id(5L).merchantCategoryName("유흥주점").policy("BLOCK").build()
        ));

        // when
        List<CategoryPolicyGroupResponseDTO> result = categoryPolicyService.getCategoryPolicyGroup(1L, "PARENT");

        // then
        assertThat(result).extracting(CategoryPolicyGroupResponseDTO::getPolicy)
                .containsExactly("ALLOW", "WATCH", "BLOCK");
    }
}