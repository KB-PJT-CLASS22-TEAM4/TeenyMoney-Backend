package com.teenyfin.teenymoney.domain.permission.service;

import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import com.teenyfin.teenymoney.domain.permission.mapper.PermissionMapper;
import com.teenyfin.teenymoney.domain.permission.vo.PermissionVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.storage.S3Storage;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PermissionServiceApproveRejectTest {

    private final PermissionMapper permissionMapper = Mockito.mock(PermissionMapper.class);
    private final MemberMapper memberMapper = Mockito.mock(MemberMapper.class);
    private final S3Storage s3Storage = Mockito.mock(S3Storage.class);
    private final PermissionService permissionService =
            new PermissionService(permissionMapper, memberMapper, s3Storage);

    private MemberVO createChildVO(Long id, String name, String profileImageKey) {
        MemberVO vo = new MemberVO();
        vo.setId(id);
        vo.setName(name);
        vo.setProfileImageKey(profileImageKey);
        return vo;
    }

    // ===== 승인 =====

    @Test
    void 부모가_승인하면_상태가_APPROVED로_변경되고_최신정보가_반환된다() {
        // given
        Long parentId = 1L;
        Long childId = 2L;
        Long permissionId = 10L;

        PermissionVO permissionVO = PermissionVO.builder()
                .id(permissionId)
                .parentId(parentId)
                .childId(childId)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();

        given(permissionMapper.selectById(permissionId)).willReturn(permissionVO);

        PermissionVO afterApprove = PermissionVO.builder()
                .id(permissionId)
                .parentId(parentId)
                .childId(childId)
                .reason("사유")
                .status("APPROVED")
                .createdAt(LocalDateTime.now())
                .build();
        given(permissionMapper.selectCreatedTodayByParentId(parentId)).willReturn(afterApprove);

        MemberVO childVO = createChildVO(childId, "김첫째", null);
        given(memberMapper.selectById(childId)).willReturn(childVO);
        given(permissionMapper.selectPermissionCategoriesByPermissionId(permissionId))
                .willReturn(List.of("PC방"));

        // when
        var result = permissionService.approvePermission(parentId, "PARENT", permissionId);

        // then
        verify(permissionMapper).updatePermissionStatus(permissionId, "APPROVED");
        assertThat(result.getIsExist()).isTrue();
        assertThat(result.getPermission().getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void 승인_대상이_아닌_부모가_승인하면_예외를_던진다() {
        // given
        Long permissionId = 10L;

        PermissionVO permissionVO = PermissionVO.builder()
                .id(permissionId)
                .parentId(999L) // 요청과 무관한 부모
                .childId(2L)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();

        given(permissionMapper.selectById(permissionId)).willReturn(permissionVO);

        // when & then
        assertThatThrownBy(() -> permissionService.approvePermission(1L, "PARENT", permissionId))
                .isInstanceOf(BusinessException.class);

        verify(permissionMapper, never()).updatePermissionStatus(anyLong(), anyString());
    }

    // ===== 거절 =====

    @Test
    void 부모가_거절하면_상태가_REJECTED로_변경되고_최신정보가_반환된다() {
        // given
        Long parentId = 1L;
        Long childId = 2L;
        Long permissionId = 10L;

        PermissionVO permissionVO = PermissionVO.builder()
                .id(permissionId)
                .parentId(parentId)
                .childId(childId)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();

        given(permissionMapper.selectById(permissionId)).willReturn(permissionVO);

        PermissionVO afterReject = PermissionVO.builder()
                .id(permissionId)
                .parentId(parentId)
                .childId(childId)
                .reason("사유")
                .status("REJECTED")
                .createdAt(LocalDateTime.now())
                .build();
        given(permissionMapper.selectCreatedTodayByParentId(parentId)).willReturn(afterReject);

        MemberVO childVO = createChildVO(childId, "김첫째", null);
        given(memberMapper.selectById(childId)).willReturn(childVO);
        given(permissionMapper.selectPermissionCategoriesByPermissionId(permissionId))
                .willReturn(List.of("PC방"));

        // when
        var result = permissionService.rejectPermission(parentId, "PARENT", permissionId);

        // then
        verify(permissionMapper).updatePermissionStatus(permissionId, "REJECTED");
        assertThat(result.getIsExist()).isTrue();
        assertThat(result.getPermission().getStatus()).isEqualTo("REJECTED");
    }

    @Test
    void 존재하지_않는_permissionId면_예외를_던진다() {
        // given
        Long permissionId = 999L;
        given(permissionMapper.selectById(permissionId)).willReturn(null);

        // when & then
        assertThatThrownBy(() -> permissionService.rejectPermission(1L, "PARENT", permissionId))
                .isInstanceOf(BusinessException.class);

        verify(permissionMapper, never()).updatePermissionStatus(anyLong(), anyString());
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
                .status("PENDING")
                .createdAt(LocalDateTime.now().minusDays(1)) // 어제 생성
                .build();

        given(permissionMapper.selectById(permissionId)).willReturn(permissionVO);

        // when & then
        assertThatThrownBy(() -> permissionService.rejectPermission(parentId, "PARENT", permissionId))
                .isInstanceOf(BusinessException.class);

        verify(permissionMapper, never()).updatePermissionStatus(anyLong(), anyString());
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
                .status("APPROVED") // 이미 처리됨
                .createdAt(LocalDateTime.now())
                .build();

        given(permissionMapper.selectById(permissionId)).willReturn(permissionVO);

        // when & then
        assertThatThrownBy(() -> permissionService.rejectPermission(parentId, "PARENT", permissionId))
                .isInstanceOf(BusinessException.class);

        verify(permissionMapper, never()).updatePermissionStatus(anyLong(), anyString());
    }
}