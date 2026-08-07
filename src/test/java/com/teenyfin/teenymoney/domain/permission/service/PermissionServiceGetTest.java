package com.teenyfin.teenymoney.domain.permission.service;

import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PermissionServiceGetTest {

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
    void 부모가_조회하면_카테고리_목록과_함께_반환된다() {
        // given
        Long parentId = 1L;
        Long childId = 2L;
        Long permissionId = 10L;
        LocalDateTime now = LocalDateTime.now();

        PermissionVO permissionVO = PermissionVO.builder()
                .id(permissionId)
                .childId(childId)
                .reason("친구 생일이라 PC방에서 놀기로 했어요")
                .status("PENDING")
                .createdAt(now)
                .build();

        MemberVO childVO = createChildVO(childId, "김민지", "profile/2.jpg");

        given(permissionMapper.selectCreatedTodayByParentId(parentId)).willReturn(permissionVO);
        given(memberMapper.selectById(childId)).willReturn(childVO);
        given(permissionMapper.selectPermissionCategoriesByPermissionId(permissionId))
                .willReturn(List.of("PC방", "노래방"));
        given(s3Storage.presignedUrl("profile/2.jpg")).willReturn("https://presigned.url/2.jpg");

        // when
        PermissionResponseWrapperDTO result = permissionService.getPermission(parentId, "PARENT");

        // then
        assertThat(result.getIsExist()).isTrue();
        assertThat(result.getPermission().getId()).isEqualTo(permissionId);
        assertThat(result.getPermission().getCategories()).containsExactly("PC방", "노래방");
        assertThat(result.getPermission().getChild().getName()).isEqualTo("김민지");
        assertThat(result.getPermission().getChild().getProfileImageUrl()).isEqualTo("https://presigned.url/2.jpg");

        verify(permissionMapper).selectCreatedTodayByParentId(parentId);
        verify(permissionMapper, never()).selectCreatedTodayByChildId(any());
    }

    @Test
    void 자녀가_조회하면_selectCreatedTodayByChildId를_호출한다() {
        // given
        Long childId = 2L;
        Long permissionId = 10L;
        LocalDateTime now = LocalDateTime.now();

        PermissionVO permissionVO = PermissionVO.builder()
                .id(permissionId)
                .childId(childId)
                .reason("사유")
                .status("PENDING")
                .createdAt(now)
                .build();

        MemberVO childVO = createChildVO(childId, "김민지", "profile/2.jpg");

        given(permissionMapper.selectCreatedTodayByChildId(childId)).willReturn(permissionVO);
        given(memberMapper.selectById(childId)).willReturn(childVO);
        given(permissionMapper.selectPermissionCategoriesByPermissionId(permissionId))
                .willReturn(List.of("PC방"));
        given(s3Storage.presignedUrl("profile/2.jpg")).willReturn("https://presigned.url/2.jpg");

        // when
        PermissionResponseWrapperDTO result = permissionService.getPermission(childId, "CHILD");

        // then
        assertThat(result.getIsExist()).isTrue();
        verify(permissionMapper).selectCreatedTodayByChildId(childId);
        verify(permissionMapper, never()).selectCreatedTodayByParentId(any());
    }

    @Test
    void 오늘_요청이_없으면_isExist_false를_반환하고_이후_로직은_실행되지_않는다() {
        // given
        Long parentId = 1L;
        given(permissionMapper.selectCreatedTodayByParentId(parentId)).willReturn(null);

        // when
        PermissionResponseWrapperDTO result = permissionService.getPermission(parentId, "PARENT");

        // then
        assertThat(result.getIsExist()).isFalse();
        assertThat(result.getPermission()).isNull();

        verify(memberMapper, never()).selectById(any());
        verify(permissionMapper, never()).selectPermissionCategoriesByPermissionId(any());
        verify(s3Storage, never()).presignedUrl(any());
    }

    @Test
    void 알수없는_role이면_예외를_던진다() {
        assertThatThrownBy(() -> permissionService.getPermission(1L, "UNKNOWN"))
                .isInstanceOf(BusinessException.class);

        verify(permissionMapper, never()).selectCreatedTodayByParentId(any());
        verify(permissionMapper, never()).selectCreatedTodayByChildId(any());
    }
}