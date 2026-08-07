package com.teenyfin.teenymoney.domain.permission.service;

import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberParentVO;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import com.teenyfin.teenymoney.domain.permission.dto.request.PermissionRequestDTO;
import com.teenyfin.teenymoney.domain.permission.dto.response.PermissionResponseWrapperDTO;
import com.teenyfin.teenymoney.domain.permission.mapper.PermissionMapper;
import com.teenyfin.teenymoney.domain.permission.vo.PermissionInsertVO;
import com.teenyfin.teenymoney.domain.permission.vo.PermissionVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.storage.S3Storage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PermissionServiceCreateTest {

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
    void 자녀가_아니면_예외를_던지고_아무것도_조회하지_않는다() {
        // given
        PermissionRequestDTO requestDTO = PermissionRequestDTO.builder()
                .categories(List.of(1L))
                .reason("사유")
                .build();

        // when & then
        assertThatThrownBy(() -> permissionService.createPermission(1L, "PARENT", requestDTO))
                .isInstanceOf(BusinessException.class);

        verify(permissionMapper, never()).selectCreatedTodayByChildId(any());
        verify(permissionMapper, never()).insertPermission(any());
        verify(permissionMapper, never()).insertPermissionCategories(any(), any());
    }

    @Test
    void 오늘_이미_요청이_있으면_예외를_던지고_insert하지_않는다() {
        // given
        Long childId = 2L;
        PermissionRequestDTO requestDTO = PermissionRequestDTO.builder()
                .categories(List.of(1L))
                .reason("사유")
                .build();

        PermissionVO existing = PermissionVO.builder()
                .id(10L)
                .childId(childId)
                .status("PENDING")
                .build();

        given(permissionMapper.selectCreatedTodayByChildId(childId)).willReturn(existing);

        // when & then
        assertThatThrownBy(() -> permissionService.createPermission(childId, "CHILD", requestDTO))
                .isInstanceOf(BusinessException.class);

        verify(memberMapper, never()).selectActiveParentByChildId(any());
        verify(permissionMapper, never()).insertPermission(any());
        verify(permissionMapper, never()).insertPermissionCategories(any(), any());
    }

    @Test
    void 정상_생성되면_요청과_카테고리가_삽입되고_생성된_정보가_조회되어_반환된다() {
        // given
        Long childId = 2L;
        Long parentId = 1L;
        Long generatedId = 100L;
        List<Long> categoryIds = List.of(1L, 2L);
        String reason = "놀러가요";

        PermissionRequestDTO requestDTO = PermissionRequestDTO.builder()
                .categories(categoryIds)
                .reason(reason)
                .build();

        PermissionVO afterInsert = PermissionVO.builder()
                .id(generatedId)
                .childId(childId)
                .reason(reason)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();

        given(permissionMapper.selectCreatedTodayByChildId(childId))
                .willReturn(null)
                .willReturn(afterInsert);

        // 여기 수정: MemberParentVO 객체를 만들어서 리턴하도록
        MemberParentVO parentVO = new MemberParentVO();
        parentVO.setParentId(parentId);
        given(memberMapper.selectActiveParentByChildId(childId)).willReturn(parentVO);

        ArgumentCaptor<PermissionInsertVO> captor = ArgumentCaptor.forClass(PermissionInsertVO.class);
        Mockito.doAnswer(invocation -> {
            PermissionInsertVO vo = invocation.getArgument(0);
            vo.setId(generatedId);
            return null;
        }).when(permissionMapper).insertPermission(captor.capture());

        MemberVO childVO = createChildVO(childId, "김첫째", null);
        given(memberMapper.selectById(childId)).willReturn(childVO);
        given(permissionMapper.selectPermissionCategoriesByPermissionId(generatedId))
                .willReturn(List.of("편의점", "카페·디저트"));

        // when
        PermissionResponseWrapperDTO result = permissionService.createPermission(childId, "CHILD", requestDTO);

        // then
        PermissionInsertVO capturedVO = captor.getValue();
        assertThat(capturedVO.getParentId()).isEqualTo(parentId);
        assertThat(capturedVO.getChildId()).isEqualTo(childId);
        assertThat(capturedVO.getReason()).isEqualTo(reason);

        verify(permissionMapper).insertPermissionCategories(eq(generatedId), eq(categoryIds));

        assertThat(result.getIsExist()).isTrue();
        assertThat(result.getPermission().getId()).isEqualTo(generatedId);
        assertThat(result.getPermission().getCategories()).containsExactly("편의점", "카페·디저트");
        assertThat(result.getPermission().getReason()).isEqualTo(reason);
        assertThat(result.getPermission().getStatus()).isEqualTo("PENDING");

        verify(permissionMapper, Mockito.times(2)).selectCreatedTodayByChildId(childId);
    }
}