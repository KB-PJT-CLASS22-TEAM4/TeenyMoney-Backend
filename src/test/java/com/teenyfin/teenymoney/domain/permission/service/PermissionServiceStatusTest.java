package com.teenyfin.teenymoney.domain.permission.service;

import com.teenyfin.teenymoney.domain.categoryPolicy.mapper.CategoryPolicyMapper;
import com.teenyfin.teenymoney.domain.categoryPolicy.vo.CategoryPolicy;
import com.teenyfin.teenymoney.domain.categoryPolicy.vo.CategoryPolicyVO;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberParentVO;
import com.teenyfin.teenymoney.domain.notification.service.NotificationService;
import com.teenyfin.teenymoney.domain.permission.dto.response.PermissionCategoryStatusResponseDTO;
import com.teenyfin.teenymoney.domain.permission.dto.response.PermissionStatusResponseDTO;
import com.teenyfin.teenymoney.domain.permission.mapper.PermissionMapper;
import com.teenyfin.teenymoney.domain.permission.vo.PermissionStatus;
import com.teenyfin.teenymoney.domain.permission.vo.PermissionVO;
import com.teenyfin.teenymoney.domain.teenyscore.mapper.TeenyScoreMapper;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreGradeVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PermissionServiceStatusTest {

    private final PermissionMapper permissionMapper = Mockito.mock(PermissionMapper.class);
    private final MemberMapper memberMapper = Mockito.mock(MemberMapper.class);
    private final TeenyScoreMapper teenyScoreMapper = Mockito.mock(TeenyScoreMapper.class);
    private final CategoryPolicyMapper categoryPolicyMapper = Mockito.mock(CategoryPolicyMapper.class);
    private final NotificationService notificationService = Mockito.mock(NotificationService.class);
    private final PermissionService permissionService = new PermissionService(
            permissionMapper, memberMapper, teenyScoreMapper, categoryPolicyMapper, notificationService);

    private TeenyScoreGradeVO gradeWithLimit(int monthlyOverrideLimit) {
        TeenyScoreGradeVO vo = new TeenyScoreGradeVO();
        vo.setMonthlyOverrideLimit(monthlyOverrideLimit);
        return vo;
    }

    private MemberParentVO parentOf(Long parentId) {
        MemberParentVO vo = new MemberParentVO();
        vo.setParentId(parentId);
        return vo;
    }

    @Test
    void 부모가_childId_없이_조회하면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> permissionService.getPermissionStatus(1L, "PARENT", null))
                .isInstanceOf(BusinessException.class);

        verify(permissionMapper, never()).countCreatedAtThisMonth(any());
    }

    @Test
    void 부모가_본인과_연동되지_않은_자녀의_childId로_조회하면_예외를_던진다() {
        // given
        Long parentId = 1L;
        Long childId = 2L;
        given(memberMapper.selectActiveParentByChildId(childId)).willReturn(parentOf(999L));

        // when & then
        assertThatThrownBy(() -> permissionService.getPermissionStatus(parentId, "PARENT", childId))
                .isInstanceOf(BusinessException.class);

        verify(permissionMapper, never()).countCreatedAtThisMonth(any());
    }

    @Test
    void 사용_횟수와_남은_횟수를_한도_기준으로_계산해서_반환한다() {
        // given
        Long childId = 2L;
        given(permissionMapper.countCreatedAtThisMonth(childId)).willReturn(3);
        given(teenyScoreMapper.selectTeenyScoreGradeByChildId(childId)).willReturn(gradeWithLimit(5));
        given(permissionMapper.selectCreatedTodayByChildId(childId)).willReturn(List.of());
        given(categoryPolicyMapper.selectByChildId(childId)).willReturn(List.of());

        // when
        PermissionStatusResponseDTO result = permissionService.getPermissionStatus(childId, "CHILD", null);

        // then
        assertThat(result.getMonthlyUsedCount()).isEqualTo(3);
        assertThat(result.getMonthlyRemainingCount()).isEqualTo(2);
    }

    @Test
    void 한도에_도달하면_남은_횟수는_0으로_표시되고_음수가_되지_않는다() {
        // given
        Long childId = 2L;
        given(permissionMapper.countCreatedAtThisMonth(childId)).willReturn(7);
        given(teenyScoreMapper.selectTeenyScoreGradeByChildId(childId)).willReturn(gradeWithLimit(5));
        given(permissionMapper.selectCreatedTodayByChildId(childId)).willReturn(List.of());
        given(categoryPolicyMapper.selectByChildId(childId)).willReturn(List.of());

        // when
        PermissionStatusResponseDTO result = permissionService.getPermissionStatus(childId, "CHILD", null);

        // then
        assertThat(result.getMonthlyRemainingCount()).isEqualTo(0);
    }

    @Test
    void 카테고리별_현재_상태를_오늘_요청_여부에_따라_계산한다() {
        // given
        Long childId = 2L;
        given(permissionMapper.countCreatedAtThisMonth(childId)).willReturn(1);
        given(teenyScoreMapper.selectTeenyScoreGradeByChildId(childId)).willReturn(gradeWithLimit(5));

        CategoryPolicyVO 편의점 = CategoryPolicyVO.builder().id(1L).categoryId(10L).categoryName("편의점").policy(CategoryPolicy.WATCH).build();
        CategoryPolicyVO pc방 = CategoryPolicyVO.builder().id(2L).categoryId(11L).categoryName("PC방").policy(CategoryPolicy.BLOCK).build();
        CategoryPolicyVO 카페 = CategoryPolicyVO.builder().id(3L).categoryId(12L).categoryName("카페").policy(CategoryPolicy.ALLOW).build();
        given(categoryPolicyMapper.selectByChildId(childId)).willReturn(List.of(편의점, pc방, 카페));

        PermissionVO pending = PermissionVO.builder()
                .id(100L).childId(childId).categoryId(11L).category("PC방")
                .status(PermissionStatus.PENDING).createdAt(LocalDateTime.now()).build();
        PermissionVO approved = PermissionVO.builder()
                .id(101L).childId(childId).categoryId(12L).category("카페")
                .status(PermissionStatus.APPROVED).createdAt(LocalDateTime.now()).build();
        given(permissionMapper.selectCreatedTodayByChildId(childId)).willReturn(List.of(pending, approved));

        // when
        PermissionStatusResponseDTO result = permissionService.getPermissionStatus(childId, "CHILD", null);

        // then
        assertThat(result.getCategories())
                .extracting(PermissionCategoryStatusResponseDTO::getCategoryId, PermissionCategoryStatusResponseDTO::getStatus)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(10L, PermissionStatus.AVAILABLE),
                        org.assertj.core.groups.Tuple.tuple(11L, PermissionStatus.PENDING),
                        org.assertj.core.groups.Tuple.tuple(12L, PermissionStatus.APPROVED)
                );
    }
}
