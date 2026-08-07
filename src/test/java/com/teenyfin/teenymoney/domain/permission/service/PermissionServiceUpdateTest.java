package com.teenyfin.teenymoney.domain.permission.service;

import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import com.teenyfin.teenymoney.domain.permission.dto.request.PermissionRequestDTO;
import com.teenyfin.teenymoney.domain.permission.dto.response.PermissionResponseWrapperDTO;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PermissionServiceUpdateTest {

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

    @Test
    void 존재하지_않는_permissionId면_예외를_던진다() {
        // given
        Long permissionId = 999L;
        PermissionRequestDTO requestDTO = PermissionRequestDTO.builder()
                .categories(List.of(1L))
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
        PermissionRequestDTO requestDTO = PermissionRequestDTO.builder()
                .categories(List.of(1L))
                .reason("수정된 사유")
                .build();

        PermissionVO permissionVO = PermissionVO.builder()
                .id(permissionId)
                .childId(999L) // memberId(2L)와 다름
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();

        given(permissionMapper.selectById(permissionId)).willReturn(permissionVO);

        // when & then
        assertThatThrownBy(() -> permissionService.updatePermission(memberId, "CHILD", permissionId, requestDTO))
                .isInstanceOf(BusinessException.class);

        verify(permissionMapper, never()).updatePermissionReason(anyLong(), any());
        verify(permissionMapper, never()).deletePermissionCategoriesByPermissionId(anyLong());
        verify(permissionMapper, never()).insertPermissionCategories(anyLong(), any());
    }

    @Test
    void 오늘_생성된_요청이_아니면_예외를_던진다() {
        // given
        Long memberId = 2L;
        Long permissionId = 10L;
        PermissionRequestDTO requestDTO = PermissionRequestDTO.builder()
                .categories(List.of(1L))
                .reason("수정된 사유")
                .build();

        PermissionVO permissionVO = PermissionVO.builder()
                .id(permissionId)
                .childId(memberId)
                .status("PENDING")
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
        PermissionRequestDTO requestDTO = PermissionRequestDTO.builder()
                .categories(List.of(1L))
                .reason("수정된 사유")
                .build();

        PermissionVO permissionVO = PermissionVO.builder()
                .id(permissionId)
                .childId(memberId)
                .status("APPROVED") // PENDING 아님
                .createdAt(LocalDateTime.now())
                .build();

        given(permissionMapper.selectById(permissionId)).willReturn(permissionVO);

        // when & then
        assertThatThrownBy(() -> permissionService.updatePermission(memberId, "CHILD", permissionId, requestDTO))
                .isInstanceOf(BusinessException.class);

        verify(permissionMapper, never()).updatePermissionReason(anyLong(), any());
    }

    @Test
    void 정상_수정되면_사유와_카테고리가_갱신되고_최신_정보가_조회되어_반환된다() {
        // given
        Long memberId = 2L;
        Long permissionId = 10L;
        List<Long> newCategoryIds = List.of(3L, 4L);
        String newReason = "수정된 사유";

        PermissionRequestDTO requestDTO = PermissionRequestDTO.builder()
                .categories(newCategoryIds)
                .reason(newReason)
                .build();

        PermissionVO beforeUpdate = PermissionVO.builder()
                .id(permissionId)
                .childId(memberId)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();

        PermissionVO afterUpdate = PermissionVO.builder()
                .id(permissionId)
                .childId(memberId)
                .reason(newReason)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();

        given(permissionMapper.selectById(permissionId)).willReturn(beforeUpdate);

        // updatePermission 내부에서 getPermission 재조회 시 사용
        given(permissionMapper.selectCreatedTodayByChildId(memberId)).willReturn(afterUpdate);

        MemberVO childVO = createChildVO(memberId, "김첫째", null);
        given(memberMapper.selectById(memberId)).willReturn(childVO);
        given(permissionMapper.selectPermissionCategoriesByPermissionId(permissionId))
                .willReturn(List.of("PC방", "노래방"));

        // when
        PermissionResponseWrapperDTO result = permissionService.updatePermission(memberId, "CHILD", permissionId, requestDTO);

        // then
        verify(permissionMapper).updatePermissionReason(permissionId, newReason);
        verify(permissionMapper).deletePermissionCategoriesByPermissionId(permissionId);
        verify(permissionMapper).insertPermissionCategories(permissionId, newCategoryIds);

        assertThat(result.getIsExist()).isTrue();
        assertThat(result.getPermission().getReason()).isEqualTo(newReason);
        assertThat(result.getPermission().getCategories()).containsExactly("PC방", "노래방");
    }
}