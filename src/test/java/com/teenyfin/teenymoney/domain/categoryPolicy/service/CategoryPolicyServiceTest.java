package com.teenyfin.teenymoney.domain.categoryPolicy.service;

import com.teenyfin.teenymoney.domain.categoryPolicy.dto.request.CategoryPolicyUpdateRequestDTO;
import com.teenyfin.teenymoney.domain.categoryPolicy.dto.response.CategoryPolicyGroupResponseDTO;
import com.teenyfin.teenymoney.domain.categoryPolicy.dto.response.CategoryPolicyParentResponseDTO;
import com.teenyfin.teenymoney.domain.categoryPolicy.dto.response.CategoryPolicyResponseDTO;
import com.teenyfin.teenymoney.domain.categoryPolicy.mapper.CategoryPolicyMapper;
import com.teenyfin.teenymoney.domain.categoryPolicy.vo.CategoryPolicy;
import com.teenyfin.teenymoney.domain.categoryPolicy.vo.CategoryPolicyVO;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberParentVO;
import com.teenyfin.teenymoney.domain.notification.service.NotificationService;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class CategoryPolicyServiceTest {

    private final CategoryPolicyMapper categoryPolicyMapper = Mockito.mock(CategoryPolicyMapper.class);
    private final MemberMapper memberMapper = Mockito.mock(MemberMapper.class);
    private final NotificationService notificationService = Mockito.mock(NotificationService.class);
    private final CategoryPolicyService categoryPolicyService =
            new CategoryPolicyService(categoryPolicyMapper, memberMapper, notificationService);

    private MemberParentVO createParentVO(Long parentId) {
        MemberParentVO vo = new MemberParentVO();
        vo.setParentId(parentId);
        return vo;
    }

    private CategoryPolicyUpdateRequestDTO updateRequest(Long id, CategoryPolicy policy) {
        CategoryPolicyUpdateRequestDTO dto = new CategoryPolicyUpdateRequestDTO();
        ReflectionTestUtils.setField(dto, "id", id);
        ReflectionTestUtils.setField(dto, "policy", policy);
        return dto;
    }

    @Test
    void 자녀가_조회하면_본인_아이디로_selectByChildId를_호출한다() {
        // given
        Long memberId = 2L;
        String role = "CHILD";

        CategoryPolicyVO vo = CategoryPolicyVO.builder()
                .id(2L)
                .categoryName("PC방")
                .policy(CategoryPolicy.BLOCK)
                .build();
        given(categoryPolicyMapper.selectByChildId(memberId)).willReturn(List.of(vo));

        // when
        List<CategoryPolicyResponseDTO> result = categoryPolicyService.getCategoryPolicy(memberId, role, null);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPolicy()).isEqualTo(CategoryPolicy.BLOCK);
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
                .categoryName("외식")
                .policy(CategoryPolicy.ALLOW)
                .build();

        given(memberMapper.selectActiveParentByChildId(childId)).willReturn(createParentVO(parentId));
        given(categoryPolicyMapper.selectByChildId(childId)).willReturn(List.of(vo));

        // when
        List<CategoryPolicyResponseDTO> result = categoryPolicyService.getCategoryPolicy(parentId, role, childId);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCategoryName()).isEqualTo("외식");
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

        given(memberMapper.selectActiveParentByChildId(childId)).willReturn(createParentVO(999L)); // 다른 부모의 자녀

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
                CategoryPolicyVO.builder().id(1L).categoryName("편의점").policy(CategoryPolicy.ALLOW).build(),
                CategoryPolicyVO.builder().id(3L).categoryName("PC방").policy(CategoryPolicy.WATCH).build(),
                CategoryPolicyVO.builder().id(5L).categoryName("유흥주점").policy(CategoryPolicy.BLOCK).build()
        ));

        // when
        List<CategoryPolicyGroupResponseDTO> result =
                categoryPolicyService.getCategoryPolicyGroup(memberId, role, null);

        // then
        assertThat(result).extracting(CategoryPolicyGroupResponseDTO::getPolicy)
                .containsExactly(CategoryPolicy.ALLOW, CategoryPolicy.WATCH, CategoryPolicy.BLOCK);
    }

    // ---------- getCategoryPolicyParentGroup() ----------

    @Test
    void 상위_카테고리_이름별로_그룹핑된다() {
        // given
        Long memberId = 2L;
        String role = "CHILD";

        given(categoryPolicyMapper.selectByChildId(memberId)).willReturn(List.of(
                CategoryPolicyVO.builder().id(1L).categoryName("편의점").parentCategoryName("음식").policy(CategoryPolicy.ALLOW).build(),
                CategoryPolicyVO.builder().id(2L).categoryName("카페·디저트").parentCategoryName("음식").policy(CategoryPolicy.ALLOW).build(),
                CategoryPolicyVO.builder().id(3L).categoryName("PC방").parentCategoryName("문화·여가").policy(CategoryPolicy.WATCH).build()
        ));

        // when
        List<CategoryPolicyParentResponseDTO> result =
                categoryPolicyService.getCategoryPolicyParentGroup(memberId, role, null);

        // then
        assertThat(result).hasSize(2);
        CategoryPolicyParentResponseDTO foodGroup = result.stream()
                .filter(g -> g.getName().equals("음식"))
                .findFirst().orElseThrow();
        assertThat(foodGroup.getCategoryPolicyList()).hasSize(2);
    }

    // ---------- updateCategoryPolicy() ----------

    @Test
    void 자녀가_수정하면_예외를_던지고_아무것도_수정하지_않는다() {
        // when & then
        assertThatThrownBy(() -> categoryPolicyService.updateCategoryPolicy(
                2L, "CHILD", 2L, List.of(updateRequest(1L, CategoryPolicy.BLOCK))))
                .isInstanceOf(BusinessException.class);

        verify(categoryPolicyMapper, never()).selectByChildId(any());
        verify(categoryPolicyMapper, never()).updateAllPolicies(any(), any(), any());
        verifyNoInteractions(notificationService);
    }

    @Test
    void 부모가_childId_없이_수정하면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> categoryPolicyService.updateCategoryPolicy(
                1L, "PARENT", null, List.of(updateRequest(1L, CategoryPolicy.BLOCK))))
                .isInstanceOf(BusinessException.class);

        verify(categoryPolicyMapper, never()).updateAllPolicies(any(), any(), any());
    }

    @Test
    void 부모가_본인_자녀가_아니면_예외를_던진다() {
        // given
        Long parentId = 1L;
        Long childId = 2L;
        given(memberMapper.selectActiveParentByChildId(childId)).willReturn(createParentVO(999L));

        // when & then
        assertThatThrownBy(() -> categoryPolicyService.updateCategoryPolicy(
                parentId, "PARENT", childId, List.of(updateRequest(1L, CategoryPolicy.BLOCK))))
                .isInstanceOf(BusinessException.class);

        verify(categoryPolicyMapper, never()).updateAllPolicies(any(), any(), any());
    }

    @Test
    void 실제로_바뀐_정책이_없으면_수정도_알림도_없이_현재_상태만_반환한다() {
        // given
        Long parentId = 1L;
        Long childId = 2L;
        given(memberMapper.selectActiveParentByChildId(childId)).willReturn(createParentVO(parentId));

        CategoryPolicyVO current = CategoryPolicyVO.builder()
                .id(1L).categoryName("편의점").parentCategoryName("음식").policy(CategoryPolicy.ALLOW).build();
        given(categoryPolicyMapper.selectByChildId(childId)).willReturn(List.of(current));

        // when: 요청 정책이 현재와 동일(ALLOW -> ALLOW)
        List<CategoryPolicyParentResponseDTO> result = categoryPolicyService.updateCategoryPolicy(
                parentId, "PARENT", childId, List.of(updateRequest(1L, CategoryPolicy.ALLOW)));

        // then
        verify(categoryPolicyMapper, never()).updateAllPolicies(any(), any(), any());
        verifyNoInteractions(notificationService);
        assertThat(result).hasSize(1);
    }

    @Test
    void 일부_정책_수정이_실패하면_예외를_던지고_알림도_보내지_않는다() {
        // given
        Long parentId = 1L;
        Long childId = 2L;

        given(memberMapper.selectActiveParentByChildId(childId)).willReturn(createParentVO(parentId));

        CategoryPolicyVO current = CategoryPolicyVO.builder()
                .id(1L).categoryName("편의점").parentCategoryName("음식").policy(CategoryPolicy.ALLOW).build();
        given(categoryPolicyMapper.selectByChildId(childId)).willReturn(List.of(current));

        List<CategoryPolicyUpdateRequestDTO> requestList = List.of(updateRequest(1L, CategoryPolicy.BLOCK));
        given(categoryPolicyMapper.updateAllPolicies(parentId, childId, requestList)).willReturn(0);

        // when & then
        assertThatThrownBy(() -> categoryPolicyService.updateCategoryPolicy(parentId, "PARENT", childId, requestList))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(notificationService);
    }

    @Test
    void 하나만_바뀌면_카테고리_이름만으로_알림_문구를_만든다() {
        // given
        Long parentId = 1L;
        Long childId = 2L;

        given(memberMapper.selectActiveParentByChildId(childId)).willReturn(createParentVO(parentId));

        CategoryPolicyVO current = CategoryPolicyVO.builder()
                .id(1L).categoryName("편의점").parentCategoryName("음식").policy(CategoryPolicy.ALLOW).build();
        given(categoryPolicyMapper.selectByChildId(childId)).willReturn(List.of(current));

        List<CategoryPolicyUpdateRequestDTO> requestList = List.of(updateRequest(1L, CategoryPolicy.BLOCK));
        given(categoryPolicyMapper.updateAllPolicies(parentId, childId, requestList)).willReturn(1);

        // when
        categoryPolicyService.updateCategoryPolicy(parentId, "PARENT", childId, requestList);

        // then
        verify(notificationService).createNotification(
                eq(childId), eq("카테고리 제한 설정이 바뀌었어요"), eq("편의점"), eq(null), eq(null), eq(true));
    }

    @Test
    void 여러건_바뀌면_첫_카테고리_이름과_외N건이_붙는다() {
        // given
        Long parentId = 1L;
        Long childId = 2L;

        given(memberMapper.selectActiveParentByChildId(childId)).willReturn(createParentVO(parentId));

        CategoryPolicyVO first = CategoryPolicyVO.builder()
                .id(1L).categoryName("편의점").parentCategoryName("음식").policy(CategoryPolicy.ALLOW).build();
        CategoryPolicyVO second = CategoryPolicyVO.builder()
                .id(2L).categoryName("PC방").parentCategoryName("문화·여가").policy(CategoryPolicy.ALLOW).build();
        given(categoryPolicyMapper.selectByChildId(childId)).willReturn(List.of(first, second));

        List<CategoryPolicyUpdateRequestDTO> requestList = List.of(
                updateRequest(1L, CategoryPolicy.BLOCK),
                updateRequest(2L, CategoryPolicy.WATCH));
        given(categoryPolicyMapper.updateAllPolicies(parentId, childId, requestList)).willReturn(2);

        // when
        categoryPolicyService.updateCategoryPolicy(parentId, "PARENT", childId, requestList);

        // then: 요청 리스트 순서상 첫 항목(편의점) 기준으로 "외 1건"이 붙는다
        verify(notificationService).createNotification(
                eq(childId), eq("카테고리 제한 설정이 바뀌었어요"), eq("편의점 외 1건"), eq(null), eq(null), eq(true));
    }
}
