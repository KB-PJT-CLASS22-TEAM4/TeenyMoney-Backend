package com.teenyfin.teenymoney.domain.permission.service;

import com.teenyfin.teenymoney.domain.categoryPolicy.mapper.CategoryPolicyMapper;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberParentVO;
import com.teenyfin.teenymoney.domain.notification.service.NotificationService;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PermissionServiceApproveRejectTest {

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

    // ===== 승인 =====

    @Test
    void 부모가_승인하면_상태가_APPROVED로_변경되고_자녀에게_알림이_발송된다() {
        // given
        Long parentId = 1L;
        Long childId = 2L;
        Long permissionId = 10L;

        PermissionVO permissionVO = PermissionVO.builder()
                .id(permissionId)
                .parentId(parentId)
                .childId(childId)
                .category("PC방")
                .status(PermissionStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        given(permissionMapper.selectById(permissionId)).willReturn(permissionVO);

        // approvePermission()이 끝나면서 getPermission(memberId, role, childId)로 재조회할 때
        // 자녀 소유권을 확인하므로, 승인한 부모가 그 자녀의 부모라는 관계를 stub해야 한다.
        given(memberMapper.selectActiveParentByChildId(childId)).willReturn(parentOf(parentId));

        PermissionVO afterApprove = PermissionVO.builder()
                .id(permissionId)
                .parentId(parentId)
                .childId(childId)
                .category("PC방")
                .reason("사유")
                .status(PermissionStatus.APPROVED)
                .createdAt(LocalDateTime.now())
                .build();
        given(permissionMapper.selectCreatedTodayByChildId(childId)).willReturn(List.of(afterApprove));

        // when
        List<PermissionResponseDTO> result = permissionService.approvePermission(parentId, "PARENT", permissionId);

        // then
        verify(permissionMapper).updatePermissionStatus(permissionId, PermissionStatus.APPROVED);
        verify(notificationService).createNotification(
                eq(childId),
                eq("오늘만 허용이 승인되었어요"),
                eq("PC방"),
                eq(NotificationReferenceType.TODAY_PERMISSION),
                eq(null),
                eq(true));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(PermissionStatus.APPROVED);
    }

    @Test
    void 승인_대상이_아닌_부모가_승인하면_예외를_던진다() {
        // given
        Long permissionId = 10L;

        PermissionVO permissionVO = PermissionVO.builder()
                .id(permissionId)
                .parentId(999L) // 요청과 무관한 부모
                .childId(2L)
                .status(PermissionStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        given(permissionMapper.selectById(permissionId)).willReturn(permissionVO);

        // when & then
        assertThatThrownBy(() -> permissionService.approvePermission(1L, "PARENT", permissionId))
                .isInstanceOf(BusinessException.class);

        verify(permissionMapper, never()).updatePermissionStatus(anyLong(), any());
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any());
    }

    // ===== 거절 =====

    @Test
    void 부모가_거절하면_상태가_REJECTED로_변경되고_자녀에게_알림이_발송된다() {
        // given
        Long parentId = 1L;
        Long childId = 2L;
        Long permissionId = 10L;

        PermissionVO permissionVO = PermissionVO.builder()
                .id(permissionId)
                .parentId(parentId)
                .childId(childId)
                .category("PC방")
                .status(PermissionStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        given(permissionMapper.selectById(permissionId)).willReturn(permissionVO);
        given(memberMapper.selectActiveParentByChildId(childId)).willReturn(parentOf(parentId));

        PermissionVO afterReject = PermissionVO.builder()
                .id(permissionId)
                .parentId(parentId)
                .childId(childId)
                .category("PC방")
                .reason("사유")
                .status(PermissionStatus.REJECTED)
                .createdAt(LocalDateTime.now())
                .build();
        given(permissionMapper.selectCreatedTodayByChildId(childId)).willReturn(List.of(afterReject));

        // when
        List<PermissionResponseDTO> result = permissionService.rejectPermission(parentId, "PARENT", permissionId);

        // then
        verify(permissionMapper).updatePermissionStatus(permissionId, PermissionStatus.REJECTED);
        verify(notificationService).createNotification(
                eq(childId),
                eq("오늘만 허용이 거절되었어요"),
                eq("PC방"),
                eq(NotificationReferenceType.TODAY_PERMISSION),
                eq(null),
                eq(true));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(PermissionStatus.REJECTED);
    }

    @Test
    void 존재하지_않는_permissionId면_예외를_던진다() {
        // given
        Long permissionId = 999L;
        given(permissionMapper.selectById(permissionId)).willReturn(null);

        // when & then
        assertThatThrownBy(() -> permissionService.rejectPermission(1L, "PARENT", permissionId))
                .isInstanceOf(BusinessException.class);

        verify(permissionMapper, never()).updatePermissionStatus(anyLong(), any());
    }

    @Test
    void 오늘_생성된_요청이_아니면_예외를_던진다() {
        // given
        Long parentId = 1L;
        Long permissionId = 10L;

        PermissionVO permissionVO = PermissionVO.builder()
                .id(permissionId)
                .parentId(parentId)
                .childId(2L)
                .status(PermissionStatus.PENDING)
                .createdAt(LocalDateTime.now().minusDays(1)) // 어제 생성
                .build();

        given(permissionMapper.selectById(permissionId)).willReturn(permissionVO);

        // when & then
        assertThatThrownBy(() -> permissionService.rejectPermission(parentId, "PARENT", permissionId))
                .isInstanceOf(BusinessException.class);

        verify(permissionMapper, never()).updatePermissionStatus(anyLong(), any());
    }

    @Test
    void PENDING_상태가_아니면_예외를_던진다() {
        // given
        Long parentId = 1L;
        Long permissionId = 10L;

        PermissionVO permissionVO = PermissionVO.builder()
                .id(permissionId)
                .parentId(parentId)
                .childId(2L)
                .status(PermissionStatus.APPROVED) // 이미 처리됨
                .createdAt(LocalDateTime.now())
                .build();

        given(permissionMapper.selectById(permissionId)).willReturn(permissionVO);

        // when & then
        assertThatThrownBy(() -> permissionService.rejectPermission(parentId, "PARENT", permissionId))
                .isInstanceOf(BusinessException.class);

        verify(permissionMapper, never()).updatePermissionStatus(anyLong(), any());
    }
}
