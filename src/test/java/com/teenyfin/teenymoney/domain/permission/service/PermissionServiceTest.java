package com.teenyfin.teenymoney.domain.permission.service;

import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
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
import static org.mockito.ArgumentMatchers.eq;
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

    @Test
    void 자녀가_아니면_예외를_던지고_아무것도_조회하지_않는다() {
        // given
        PermissionRequestDTO requestDTO = PermissionRequestDTO.builder().build(); // 실제 생성자/빌더에 맞게 수정 필요

        // when & then
        assertThatThrownBy(() -> permissionService.createPermission(1L, "PARENT", requestDTO))
                .isInstanceOf(BusinessException.class);

        verify(permissionMapper, never()).selectCreatedTodayByChildId(any());
        verify(permissionMapper, never()).insertPermissionRequest(any());
        verify(permissionMapper, never()).insertPermissionRequestCategories(any(), any());
    }

    @Test
    void 오늘_이미_요청이_있으면_예외를_던지고_insert하지_않는다() {
        // given
        Long childId = 2L;
        PermissionRequestDTO requestDTO = PermissionRequestDTO.builder().build(); // reason, categories 세팅 필요

        List<PermissionVO> existing = List.of(
                PermissionVO.builder()
                        .id(10L)
                        .childId(childId)
                        .categoryName("편의점")
                        .status("PENDING")
                        .build()
        );
        given(permissionMapper.selectCreatedTodayByChildId(childId)).willReturn(existing);

        // when & then
        assertThatThrownBy(() -> permissionService.createPermission(childId, "CHILD", requestDTO))
                .isInstanceOf(BusinessException.class);

        verify(permissionMapper, never()).selectParentIdByChildId(any());
        verify(permissionMapper, never()).insertPermissionRequest(any());
        verify(permissionMapper, never()).insertPermissionRequestCategories(any(), any());
    }

    @Test
    void 정상_생성되면_요청과_카테고리가_삽입되고_생성된_정보가_조회되어_반환된다() {
        // given
        Long childId = 2L;
        Long parentId = 1L;
        List<Long> categoryIds = List.of(1L, 2L);
        String reason = "놀러가요";

        PermissionRequestDTO requestDTO = PermissionRequestDTO.builder()
                .categories(categoryIds)
                .reason(reason)
                .build(); // 실제 DTO 구조에 맞게 수정 필요

        // 1) 중복 체크 시점: 아직 없음
        given(permissionMapper.selectCreatedTodayByChildId(childId))
                .willReturn(List.of())      // createPermission 안의 첫 번째 호출
                .willReturn(List.of(         // getPermission 안의 두 번째 호출 (insert 이후)
                        PermissionVO.builder()
                                .id(100L)
                                .childId(childId)
                                .categoryName("편의점")
                                .reason(reason)
                                .status("PENDING")
                                .createdAt(LocalDateTime.now())
                                .build(),
                        PermissionVO.builder()
                                .id(100L)
                                .childId(childId)
                                .categoryName("카페·디저트")
                                .reason(reason)
                                .status("PENDING")
                                .createdAt(LocalDateTime.now())
                                .build()
                ));

        given(permissionMapper.selectParentIdByChildId(childId)).willReturn(parentId);

        // insertPermissionRequest 호출 시, MyBatis useGeneratedKeys처럼 id를 채워주는 걸 흉내냄
        ArgumentCaptor<PermissionInsertVO> captor = ArgumentCaptor.forClass(PermissionInsertVO.class);
        Mockito.doAnswer(invocation -> {
            PermissionInsertVO vo = invocation.getArgument(0);
            vo.setId(100L); // insert 후 생성된 PK를 채워주는 것처럼 흉내
            return null;
        }).when(permissionMapper).insertPermissionRequest(captor.capture());

        MemberVO childVO = createChildVO(childId, "김첫째", null);
        given(memberMapper.selectById(childId)).willReturn(childVO);

        // when
        PermissionResponseWrapperDTO result = permissionService.createPermission(childId, "CHILD", requestDTO);

        // then
        // 1. insert가 올바른 값으로 호출되었는지
        PermissionInsertVO capturedVO = captor.getValue();
        assertThat(capturedVO.getParentId()).isEqualTo(parentId);
        assertThat(capturedVO.getChildId()).isEqualTo(childId);
        assertThat(capturedVO.getReason()).isEqualTo(reason);

        // 2. 카테고리 insert가 생성된 id(100L)와 요청받은 categoryIds로 호출되었는지
        verify(permissionMapper).insertPermissionRequestCategories(eq(100L), eq(categoryIds));

        // 3. 최종 응답이 방금 생성한 데이터를 그대로 반영하는지
        assertThat(result.getIsExist()).isTrue();
        assertThat(result.getPermission().getId()).isEqualTo(100L);
        assertThat(result.getPermission().getCategories()).containsExactly("편의점", "카페·디저트");
        assertThat(result.getPermission().getReason()).isEqualTo(reason);
        assertThat(result.getPermission().getStatus()).isEqualTo("PENDING");

        // 4. selectCreatedTodayByChildId가 총 두 번 호출됐는지 (중복체크용 + 재조회용)
        verify(permissionMapper, Mockito.times(2)).selectCreatedTodayByChildId(childId);
    }
}