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

class PermissionServiceTest {

    private final PermissionMapper permissionMapper = Mockito.mock(PermissionMapper.class);
    private final MemberMapper memberMapper = Mockito.mock(MemberMapper.class);
    private final S3Storage s3Storage = Mockito.mock(S3Storage.class);
    private final PermissionService permissionService =
            new PermissionService(permissionMapper, memberMapper, s3Storage);

    // MemberVO는 @Builder가 없어서 기본 생성자 + setter로 조립하는 테스트 전용 헬퍼
    private MemberVO createChildVO(Long id, String name, String profileImageKey) {
        MemberVO vo = new MemberVO();
        vo.setId(id);
        vo.setName(name);
        vo.setProfileImageKey(profileImageKey);
        return vo;
    }

    @Test
    void 부모가_조회하면_오늘의_요청이_카테고리_리스트로_합쳐져서_반환된다() {
        // given
        Long parentId = 1L;
        Long childId = 2L;
        LocalDateTime now = LocalDateTime.now();

        List<PermissionVO> permissionVOList = List.of(
                PermissionVO.builder()
                        .id(10L)
                        .childId(childId)
                        .categoryName("PC방")
                        .reason("친구 생일이라 PC방에서 놀기로 했어요")
                        .status("PENDING")
                        .createdAt(now)
                        .build(),
                PermissionVO.builder()
                        .id(10L)
                        .childId(childId)
                        .categoryName("노래방")
                        .reason("친구 생일이라 PC방에서 놀기로 했어요")
                        .status("PENDING")
                        .createdAt(now)
                        .build()
        );

        MemberVO childVO = createChildVO(childId, "김민지", "profile/2.jpg");

        given(permissionMapper.selectCreatedTodayByParentId(parentId)).willReturn(permissionVOList);
        given(memberMapper.selectById(childId)).willReturn(childVO);
        given(s3Storage.presignedUrl("profile/2.jpg")).willReturn("https://presigned.url/2.jpg");

        // when
        PermissionResponseWrapperDTO result = permissionService.getPermission(parentId, "PARENT");

        // then
        assertThat(result.getIsExist()).isTrue();
        assertThat(result.getPermission().getId()).isEqualTo(10L);
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
        LocalDateTime now = LocalDateTime.now();

        List<PermissionVO> permissionVOList = List.of(
                PermissionVO.builder()
                        .id(10L)
                        .childId(childId)
                        .categoryName("PC방")
                        .reason("사유")
                        .status("PENDING")
                        .createdAt(now)
                        .build()
        );

        MemberVO childVO = createChildVO(childId, "김민지", "profile/2.jpg");

        given(permissionMapper.selectCreatedTodayByChildId(childId)).willReturn(permissionVOList);
        given(memberMapper.selectById(childId)).willReturn(childVO);
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
        given(permissionMapper.selectCreatedTodayByParentId(parentId)).willReturn(List.of());

        // when
        PermissionResponseWrapperDTO result = permissionService.getPermission(parentId, "PARENT");

        // then
        assertThat(result.getIsExist()).isFalse();
        assertThat(result.getPermission()).isNull();

        verify(memberMapper, never()).selectById(any());
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