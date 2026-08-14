package com.teenyfin.teenymoney.domain.permission.service;

import com.teenyfin.teenymoney.domain.categoryPolicy.mapper.CategoryPolicyMapper;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberParentVO;
import com.teenyfin.teenymoney.domain.notification.service.NotificationService;
import com.teenyfin.teenymoney.domain.permission.dto.response.PermissionResponseDTO;
import com.teenyfin.teenymoney.domain.permission.mapper.PermissionMapper;
import com.teenyfin.teenymoney.domain.permission.vo.PermissionStatus;
import com.teenyfin.teenymoney.domain.permission.vo.PermissionVO;
import com.teenyfin.teenymoney.domain.teenyscore.mapper.TeenyScoreMapper;
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

class PermissionServiceGetTest {

    private final PermissionMapper permissionMapper = Mockito.mock(PermissionMapper.class);
    private final MemberMapper memberMapper = Mockito.mock(MemberMapper.class);
    private final TeenyScoreMapper teenyScoreMapper = Mockito.mock(TeenyScoreMapper.class);
    private final CategoryPolicyMapper categoryPolicyMapper = Mockito.mock(CategoryPolicyMapper.class);
    private final NotificationService notificationService = Mockito.mock(NotificationService.class);
    private final PermissionService permissionService = new PermissionService(
            permissionMapper, memberMapper, teenyScoreMapper, categoryPolicyMapper, notificationService);

    private MemberParentVO parentOf(Long parentId) {
        MemberParentVO vo = new MemberParentVO();
        vo.setParentId(parentId);
        return vo;
    }

    @Test
    void 자녀가_조회하면_childId_파라미터와_무관하게_본인_기준으로_조회한다() {
        // given
        Long childId = 2L;
        LocalDateTime now = LocalDateTime.now();

        PermissionVO permissionVO = PermissionVO.builder()
                .id(10L)
                .childId(childId)
                .category("PC방·노래방")
                .reason("사유")
                .status(PermissionStatus.PENDING)
                .createdAt(now)
                .build();

        given(permissionMapper.selectCreatedTodayByChildId(childId)).willReturn(List.of(permissionVO));

        // when: 자녀는 childId를 넘겨도 무시되고 본인 id로 조회돼야 한다
        List<PermissionResponseDTO> result = permissionService.getPermission(childId, "CHILD", 999L);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(10L);
        assertThat(result.get(0).getCategory()).isEqualTo("PC방·노래방");
        assertThat(result.get(0).getReason()).isEqualTo("사유");
        assertThat(result.get(0).getStatus()).isEqualTo(PermissionStatus.PENDING);

        verify(permissionMapper).selectCreatedTodayByChildId(childId);
        verify(memberMapper, never()).selectActiveParentByChildId(any());
    }

    @Test
    void 부모가_childId_없이_조회하면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> permissionService.getPermission(1L, "PARENT", null))
                .isInstanceOf(BusinessException.class);

        verify(permissionMapper, never()).selectCreatedTodayByChildId(any());
    }

    @Test
    void 부모가_본인과_연동되지_않은_자녀의_childId로_조회하면_예외를_던진다() {
        // given
        Long parentId = 1L;
        Long childId = 2L;

        given(memberMapper.selectActiveParentByChildId(childId)).willReturn(parentOf(999L)); // 다른 부모

        // when & then
        assertThatThrownBy(() -> permissionService.getPermission(parentId, "PARENT", childId))
                .isInstanceOf(BusinessException.class);

        verify(permissionMapper, never()).selectCreatedTodayByChildId(any());
    }

    @Test
    void 부모가_본인_자녀의_childId로_조회하면_그_자녀_기준으로_목록을_조회한다() {
        // given
        Long parentId = 1L;
        Long childId = 2L;
        LocalDateTime now = LocalDateTime.now();

        given(memberMapper.selectActiveParentByChildId(childId)).willReturn(parentOf(parentId));

        PermissionVO first = PermissionVO.builder()
                .id(10L).childId(childId).category("PC방").reason("사유1").status(PermissionStatus.PENDING).createdAt(now)
                .build();
        PermissionVO second = PermissionVO.builder()
                .id(11L).childId(childId).category("노래방").reason("사유2").status(PermissionStatus.PENDING).createdAt(now)
                .build();
        given(permissionMapper.selectCreatedTodayByChildId(childId)).willReturn(List.of(first, second));

        // when
        List<PermissionResponseDTO> result = permissionService.getPermission(parentId, "PARENT", childId);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(PermissionResponseDTO::getCategory)
                .containsExactly("PC방", "노래방");

        verify(permissionMapper).selectCreatedTodayByChildId(childId);
    }

    @Test
    void 오늘_요청이_없으면_빈_목록을_반환한다() {
        // given
        Long childId = 2L;
        given(permissionMapper.selectCreatedTodayByChildId(childId)).willReturn(List.of());

        // when
        List<PermissionResponseDTO> result = permissionService.getPermission(childId, "CHILD", null);

        // then
        assertThat(result).isEmpty();
    }
}