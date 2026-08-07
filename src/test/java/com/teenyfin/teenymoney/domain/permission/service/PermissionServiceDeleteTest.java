package com.teenyfin.teenymoney.domain.permission.service;

import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.permission.mapper.PermissionMapper;
import com.teenyfin.teenymoney.domain.permission.vo.PermissionVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.storage.S3Storage;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PermissionServiceDeleteTest {

    private final PermissionMapper permissionMapper = Mockito.mock(PermissionMapper.class);
    private final MemberMapper memberMapper = Mockito.mock(MemberMapper.class);
    private final S3Storage s3Storage = Mockito.mock(S3Storage.class);
    private final PermissionService permissionService =
            new PermissionService(permissionMapper, memberMapper, s3Storage);

    @Test
    void 존재하지_않는_permissionId면_예외를_던진다() {
        // given
        Long permissionId = 999L;
        given(permissionMapper.selectById(permissionId)).willReturn(null);

        // when & then
        assertThatThrownBy(() -> permissionService.deletePermission(2L, "CHILD", permissionId))
                .isInstanceOf(BusinessException.class);

        verify(permissionMapper, never()).deletePermissionCategoriesByPermissionId(anyLong());
        verify(permissionMapper, never()).deletePermissionById(anyLong());
    }

    @Test
    void 본인이_생성한_요청이_아니면_예외를_던진다() {
        // given
        Long memberId = 2L;
        Long permissionId = 10L;

        PermissionVO permissionVO = PermissionVO.builder()
                .id(permissionId)
                .childId(999L) // memberId(2L)와 다름
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();

        given(permissionMapper.selectById(permissionId)).willReturn(permissionVO);

        // when & then
        assertThatThrownBy(() -> permissionService.deletePermission(memberId, "CHILD", permissionId))
                .isInstanceOf(BusinessException.class);

        verify(permissionMapper, never()).deletePermissionCategoriesByPermissionId(anyLong());
        verify(permissionMapper, never()).deletePermissionById(anyLong());
    }

    @Test
    void 오늘_생성된_요청이_아니면_예외를_던진다() {
        // given
        Long memberId = 2L;
        Long permissionId = 10L;

        PermissionVO permissionVO = PermissionVO.builder()
                .id(permissionId)
                .childId(memberId)
                .status("PENDING")
                .createdAt(LocalDateTime.now().minusDays(1)) // 어제 생성
                .build();

        given(permissionMapper.selectById(permissionId)).willReturn(permissionVO);

        // when & then
        assertThatThrownBy(() -> permissionService.deletePermission(memberId, "CHILD", permissionId))
                .isInstanceOf(BusinessException.class);

        verify(permissionMapper, never()).deletePermissionCategoriesByPermissionId(anyLong());
        verify(permissionMapper, never()).deletePermissionById(anyLong());
    }

    @Test
    void PENDING_상태가_아니면_예외를_던진다() {
        // given
        Long memberId = 2L;
        Long permissionId = 10L;

        PermissionVO permissionVO = PermissionVO.builder()
                .id(permissionId)
                .childId(memberId)
                .status("APPROVED") // PENDING 아님
                .createdAt(LocalDateTime.now())
                .build();

        given(permissionMapper.selectById(permissionId)).willReturn(permissionVO);

        // when & then
        assertThatThrownBy(() -> permissionService.deletePermission(memberId, "CHILD", permissionId))
                .isInstanceOf(BusinessException.class);

        verify(permissionMapper, never()).deletePermissionCategoriesByPermissionId(anyLong());
        verify(permissionMapper, never()).deletePermissionById(anyLong());
    }

    @Test
    void 정상_삭제되면_카테고리와_요청_row가_순서대로_삭제된다() {
        // given
        Long memberId = 2L;
        Long permissionId = 10L;

        PermissionVO permissionVO = PermissionVO.builder()
                .id(permissionId)
                .childId(memberId)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();

        given(permissionMapper.selectById(permissionId)).willReturn(permissionVO);

        // when
        permissionService.deletePermission(memberId, "CHILD", permissionId);

        // then
        verify(permissionMapper).deletePermissionCategoriesByPermissionId(permissionId);
        verify(permissionMapper).deletePermissionById(permissionId);
    }
}