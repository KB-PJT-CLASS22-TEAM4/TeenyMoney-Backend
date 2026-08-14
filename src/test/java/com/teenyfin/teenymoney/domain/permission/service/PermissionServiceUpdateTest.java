package com.teenyfin.teenymoney.domain.permission.service;

import com.teenyfin.teenymoney.domain.categoryPolicy.mapper.CategoryPolicyMapper;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.notification.service.NotificationService;
import com.teenyfin.teenymoney.domain.permission.dto.request.PermissionUpdateRequestDTO;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PermissionServiceUpdateTest {

    private final PermissionMapper permissionMapper = Mockito.mock(PermissionMapper.class);
    private final MemberMapper memberMapper = Mockito.mock(MemberMapper.class);
    private final TeenyScoreMapper teenyScoreMapper = Mockito.mock(TeenyScoreMapper.class);
    private final CategoryPolicyMapper categoryPolicyMapper = Mockito.mock(CategoryPolicyMapper.class);
    private final NotificationService notificationService = Mockito.mock(NotificationService.class);
    private final PermissionService permissionService = new PermissionService(
            permissionMapper, memberMapper, teenyScoreMapper, categoryPolicyMapper, notificationService);

    @Test
    void 존재하지_않는_permissionId면_예외를_던진다() {
        // given
        Long permissionId = 999L;
        PermissionUpdateRequestDTO requestDTO = PermissionUpdateRequestDTO.builder()
                .reason("사유")
                .build();

        given(permissionMapper.selectById(permissionId)).willReturn(null);

        // when & then
        assertThatThrownBy(() -> permissionService.updatePermission(2L, "CHILD", permissionId, requestDTO))
                .isInstanceOf(BusinessException.class);

        verify(permissionMapper, never()).updatePermissionReason(anyLong(), any());
    }

    @Test
    void 본인이_생성한_요청이_아니면_예외를_던진다() {
        // given
        Long memberId = 2L;
        Long permissionId = 10L;
        PermissionUpdateRequestDTO requestDTO = PermissionUpdateRequestDTO.builder()
                .reason("수정된 사유")
                .build();

        PermissionVO permissionVO = PermissionVO.builder()
                .id(permissionId)
                .childId(999L) // memberId(2L)와 다름
                .status(PermissionStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        given(permissionMapper.selectById(permissionId)).willReturn(permissionVO);

        // when & then
        assertThatThrownBy(() -> permissionService.updatePermission(memberId, "CHILD", permissionId, requestDTO))
                .isInstanceOf(BusinessException.class);

        verify(permissionMapper, never()).updatePermissionReason(anyLong(), any());
    }

    @Test
    void 오늘_생성된_요청이_아니면_예외를_던진다() {
        // given
        Long memberId = 2L;
        Long permissionId = 10L;
        PermissionUpdateRequestDTO requestDTO = PermissionUpdateRequestDTO.builder()
                .reason("수정된 사유")
                .build();

        PermissionVO permissionVO = PermissionVO.builder()
                .id(permissionId)
                .childId(memberId)
                .status(PermissionStatus.PENDING)
                .createdAt(LocalDateTime.now().minusDays(1)) // 어제 생성
                .build();

        given(permissionMapper.selectById(permissionId)).willReturn(permissionVO);

        // when & then
        assertThatThrownBy(() -> permissionService.updatePermission(memberId, "CHILD", permissionId, requestDTO))
                .isInstanceOf(BusinessException.class);

        verify(permissionMapper, never()).updatePermissionReason(anyLong(), any());
    }

    @Test
    void PENDING_상태가_아니면_예외를_던진다() {
        // given
        Long memberId = 2L;
        Long permissionId = 10L;
        PermissionUpdateRequestDTO requestDTO = PermissionUpdateRequestDTO.builder()
                .reason("수정된 사유")
                .build();

        PermissionVO permissionVO = PermissionVO.builder()
                .id(permissionId)
                .childId(memberId)
                .status(PermissionStatus.APPROVED) // PENDING 아님
                .createdAt(LocalDateTime.now())
                .build();

        given(permissionMapper.selectById(permissionId)).willReturn(permissionVO);

        // when & then
        assertThatThrownBy(() -> permissionService.updatePermission(memberId, "CHILD", permissionId, requestDTO))
                .isInstanceOf(BusinessException.class);

        verify(permissionMapper, never()).updatePermissionReason(anyLong(), any());
    }

    @Test
    void 정상_수정되면_사유가_갱신되고_최신_정보가_조회되어_반환된다() {
        // given
        Long memberId = 2L;
        Long permissionId = 10L;
        String newReason = "수정된 사유";

        PermissionUpdateRequestDTO requestDTO = PermissionUpdateRequestDTO.builder()
                .reason(newReason)
                .build();

        PermissionVO beforeUpdate = PermissionVO.builder()
                .id(permissionId)
                .childId(memberId)
                .status(PermissionStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        given(permissionMapper.selectById(permissionId)).willReturn(beforeUpdate);

        // updatePermission 내부에서 getPermission(memberId, role, null) 재조회 시 사용
        PermissionVO afterUpdate = PermissionVO.builder()
                .id(permissionId)
                .childId(memberId)
                .category("PC방")
                .reason(newReason)
                .status(PermissionStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        given(permissionMapper.selectCreatedTodayByChildId(memberId)).willReturn(List.of(afterUpdate));

        // when
        List<PermissionResponseDTO> result =
                permissionService.updatePermission(memberId, "CHILD", permissionId, requestDTO);

        // then
        verify(permissionMapper).updatePermissionReason(permissionId, newReason);
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getReason()).isEqualTo(newReason);
        assertThat(result.get(0).getCategory()).isEqualTo("PC방");
    }
}
