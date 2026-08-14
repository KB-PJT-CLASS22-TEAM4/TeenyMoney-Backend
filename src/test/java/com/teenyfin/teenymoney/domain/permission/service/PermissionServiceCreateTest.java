package com.teenyfin.teenymoney.domain.permission.service;

import com.teenyfin.teenymoney.domain.categoryPolicy.mapper.CategoryPolicyMapper;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberParentVO;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import com.teenyfin.teenymoney.domain.notification.service.NotificationService;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;
import com.teenyfin.teenymoney.domain.permission.dto.request.PermissionRequestDTO;
import com.teenyfin.teenymoney.domain.permission.dto.response.PermissionResponseDTO;
import com.teenyfin.teenymoney.domain.permission.mapper.PermissionMapper;
import com.teenyfin.teenymoney.domain.permission.vo.PermissionInsertVO;
import com.teenyfin.teenymoney.domain.permission.vo.PermissionStatus;
import com.teenyfin.teenymoney.domain.permission.vo.PermissionVO;
import com.teenyfin.teenymoney.domain.teenyscore.mapper.TeenyScoreMapper;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreGradeVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

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
    private final TeenyScoreMapper teenyScoreMapper = Mockito.mock(TeenyScoreMapper.class);
    private final CategoryPolicyMapper categoryPolicyMapper = Mockito.mock(CategoryPolicyMapper.class);
    private final NotificationService notificationService = Mockito.mock(NotificationService.class);
    private final PermissionService permissionService = new PermissionService(
            permissionMapper, memberMapper, teenyScoreMapper, categoryPolicyMapper, notificationService);

    private TeenyScoreGradeVO teenyScoreGradeWithLimit(int monthlyOverrideLimit) {
        TeenyScoreGradeVO vo = new TeenyScoreGradeVO();
        vo.setMonthlyOverrideLimit(monthlyOverrideLimit);
        return vo;
    }

    // insertPermission()을 호출할 때마다 순서대로 다른 id를 채워준다 (useGeneratedKeys 흉내)
    private void stubGeneratedIds(long startId) {
        AtomicLong nextId = new AtomicLong(startId);
        Mockito.doAnswer(invocation -> {
            PermissionInsertVO vo = invocation.getArgument(0);
            ReflectionTestUtils.setField(vo, "id", nextId.getAndIncrement());
            return null;
        }).when(permissionMapper).insertPermission(any());
    }

    @Test
    void 자녀가_아니면_예외를_던지고_아무것도_삽입하지_않는다() {
        // given
        PermissionRequestDTO requestDTO = PermissionRequestDTO.builder()
                .categories(List.of(1L))
                .reason("사유")
                .build();

        // when & then
        assertThatThrownBy(() -> permissionService.createPermission(1L, "PARENT", requestDTO))
                .isInstanceOf(BusinessException.class);

        verify(permissionMapper, never()).countCreatedAtThisMonth(any());
        verify(teenyScoreMapper, never()).selectTeenyScoreGradeByChildId(any());
        verify(permissionMapper, never()).insertPermission(any());
        verify(permissionMapper, never()).insertPermissionCategory(any(), any());
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any());
    }

    @Test
    void 이번_달_요청_횟수가_등급별_월간_한도에_도달하면_예외를_던지고_아무것도_삽입하지_않는다() {
        // given
        Long childId = 2L;
        PermissionRequestDTO requestDTO = PermissionRequestDTO.builder()
                .categories(List.of(1L))
                .reason("사유")
                .build();

        given(permissionMapper.countCreatedAtThisMonth(childId)).willReturn(5);
        given(teenyScoreMapper.selectTeenyScoreGradeByChildId(childId)).willReturn(teenyScoreGradeWithLimit(5));

        // when & then
        assertThatThrownBy(() -> permissionService.createPermission(childId, "CHILD", requestDTO))
                .isInstanceOf(BusinessException.class);

        verify(memberMapper, never()).selectActiveParentByChildId(any());
        verify(permissionMapper, never()).insertPermission(any());
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any());
    }

    @Test
    void 카테고리가_비어있으면_삽입도_알림도_없이_현재_목록만_반환한다() {
        // given
        Long childId = 2L;
        Long parentId = 1L;
        PermissionRequestDTO requestDTO = PermissionRequestDTO.builder()
                .categories(List.of())
                .reason("사유")
                .build();

        given(permissionMapper.countCreatedAtThisMonth(childId)).willReturn(0);
        given(teenyScoreMapper.selectTeenyScoreGradeByChildId(childId)).willReturn(teenyScoreGradeWithLimit(5));

        MemberParentVO parentVO = new MemberParentVO();
        parentVO.setParentId(parentId);
        given(memberMapper.selectActiveParentByChildId(childId)).willReturn(parentVO);

        given(permissionMapper.selectCreatedTodayByChildId(childId)).willReturn(List.of());

        // when
        List<PermissionResponseDTO> result = permissionService.createPermission(childId, "CHILD", requestDTO);

        // then
        assertThat(result).isEmpty();
        verify(permissionMapper, never()).insertPermission(any());
        verify(permissionMapper, never()).insertPermissionCategory(any(), any());
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any());
    }

    @Test
    void 정상_생성되면_카테고리마다_요청_row가_삽입되고_부모에게_알림이_발송된다() {
        // given
        Long childId = 2L;
        Long parentId = 1L;
        List<Long> categoryIds = List.of(1L, 2L);
        String reason = "놀러가요";

        PermissionRequestDTO requestDTO = PermissionRequestDTO.builder()
                .categories(categoryIds)
                .reason(reason)
                .build();

        given(permissionMapper.countCreatedAtThisMonth(childId)).willReturn(4);
        given(teenyScoreMapper.selectTeenyScoreGradeByChildId(childId)).willReturn(teenyScoreGradeWithLimit(5));

        MemberParentVO parentVO = new MemberParentVO();
        parentVO.setParentId(parentId);
        given(memberMapper.selectActiveParentByChildId(childId)).willReturn(parentVO);

        stubGeneratedIds(100L);

        MemberVO childVO = new MemberVO();
        childVO.setId(childId);
        childVO.setName("김첫째");
        given(memberMapper.selectById(childId)).willReturn(childVO);
        given(categoryPolicyMapper.selectCategoryNameById(1L)).willReturn("편의점");

        // createPermission()이 끝나면서 getPermission()으로 재조회할 때 반환할 값
        PermissionVO afterInsert = PermissionVO.builder()
                .id(100L).childId(childId).category("편의점").reason(reason)
                .status(PermissionStatus.PENDING).createdAt(LocalDateTime.now())
                .build();
        given(permissionMapper.selectCreatedTodayByChildId(childId)).willReturn(List.of(afterInsert));

        // when
        List<PermissionResponseDTO> result = permissionService.createPermission(childId, "CHILD", requestDTO);

        // then: 카테고리 개수(2개)만큼 permission row와 category row가 각각 삽입된다
        ArgumentCaptor<PermissionInsertVO> insertCaptor = ArgumentCaptor.forClass(PermissionInsertVO.class);
        verify(permissionMapper, Mockito.times(2)).insertPermission(insertCaptor.capture());
        for (PermissionInsertVO captured : insertCaptor.getAllValues()) {
            assertThat(captured.getParentId()).isEqualTo(parentId);
            assertThat(captured.getChildId()).isEqualTo(childId);
            assertThat(captured.getReason()).isEqualTo(reason);
        }

        verify(permissionMapper).insertPermissionCategory(100L, 1L);
        verify(permissionMapper).insertPermissionCategory(101L, 2L);

        // then: 부모에게 첫 카테고리 이름 + "외 1건" 문구로 알림이 발송된다
        verify(notificationService).createNotification(
                eq(parentId),
                eq("자녀가 오늘만 허용을 요청했어요"),
                eq("김첫째 · 편의점 외 1건"),
                eq(NotificationReferenceType.TODAY_PERMISSION),
                eq(null),
                eq(true));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getReason()).isEqualTo(reason);
        assertThat(result.get(0).getStatus()).isEqualTo(PermissionStatus.PENDING);
    }

    @Test
    void 카테고리가_하나면_알림_문구에_외N건이_붙지_않는다() {
        // given
        Long childId = 2L;
        Long parentId = 1L;
        String reason = "사유";

        PermissionRequestDTO requestDTO = PermissionRequestDTO.builder()
                .categories(List.of(1L))
                .reason(reason)
                .build();

        given(permissionMapper.countCreatedAtThisMonth(childId)).willReturn(0);
        given(teenyScoreMapper.selectTeenyScoreGradeByChildId(childId)).willReturn(teenyScoreGradeWithLimit(5));

        MemberParentVO parentVO = new MemberParentVO();
        parentVO.setParentId(parentId);
        given(memberMapper.selectActiveParentByChildId(childId)).willReturn(parentVO);

        stubGeneratedIds(200L);

        MemberVO childVO = new MemberVO();
        childVO.setId(childId);
        childVO.setName("김첫째");
        given(memberMapper.selectById(childId)).willReturn(childVO);
        given(categoryPolicyMapper.selectCategoryNameById(1L)).willReturn("PC방");

        given(permissionMapper.selectCreatedTodayByChildId(childId)).willReturn(List.of());

        // when
        permissionService.createPermission(childId, "CHILD", requestDTO);

        // then
        verify(notificationService).createNotification(
                eq(parentId),
                any(),
                eq("김첫째 · PC방"),
                eq(NotificationReferenceType.TODAY_PERMISSION),
                eq(null),
                eq(true));
    }
}
