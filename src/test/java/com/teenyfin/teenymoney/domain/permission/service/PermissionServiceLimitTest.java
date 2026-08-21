package com.teenyfin.teenymoney.domain.permission.service;

import com.teenyfin.teenymoney.domain.categoryPolicy.mapper.CategoryPolicyMapper;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberParentVO;
import com.teenyfin.teenymoney.domain.notification.service.NotificationService;
import com.teenyfin.teenymoney.domain.permission.dto.request.PermissionLimitUpdateRequestDTO;
import com.teenyfin.teenymoney.domain.permission.dto.request.PermissionRequestDTO;
import com.teenyfin.teenymoney.domain.permission.dto.response.PermissionLimitResponseDTO;
import com.teenyfin.teenymoney.domain.permission.dto.response.PermissionStatusResponseDTO;
import com.teenyfin.teenymoney.domain.permission.mapper.PermissionMapper;
import com.teenyfin.teenymoney.domain.permission.vo.PermissionLimitVO;
import com.teenyfin.teenymoney.domain.teenyscore.mapper.TeenyScoreMapper;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreGradeVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PermissionServiceLimitTest {
    private PermissionMapper permissionMapper;
    private MemberMapper memberMapper;
    private TeenyScoreMapper teenyScoreMapper;
    private PermissionService service;

    @BeforeEach
    void setUp() {
        permissionMapper = mock(PermissionMapper.class);
        memberMapper = mock(MemberMapper.class);
        teenyScoreMapper = mock(TeenyScoreMapper.class);
        service = new PermissionService(permissionMapper, memberMapper, teenyScoreMapper,
                mock(CategoryPolicyMapper.class), mock(NotificationService.class));
    }

    @Test
    @DisplayName("부모 설정 전에는 티니등급 기본 한도와 남은 일수를 반환한다")
    void 설정값이_없으면_등급_기본_한도를_조회한다() {
        given(memberMapper.selectActiveParentByChildId(2L)).willReturn(parent(1L));
        given(permissionMapper.selectParentMonthlyLimit(2L)).willReturn(new PermissionLimitVO());
        given(teenyScoreMapper.selectTeenyScoreGradeByChildId(2L)).willReturn(grade(5));
        given(permissionMapper.countCreatedAtThisMonth(2L)).willReturn(2);

        PermissionLimitResponseDTO result = service.getMonthlyLimit(1L, "PARENT", 2L);

        assertThat(result.getGradeDefaultLimit()).isEqualTo(5);
        assertThat(result.getParentConfiguredLimit()).isNull();
        assertThat(result.getEffectiveLimit()).isEqualTo(5);
        assertThat(result.getRemainingDays()).isEqualTo(3);
        assertThat(result.isCustomizedByParent()).isFalse();
    }

    @Test
    @DisplayName("연결된 부모가 설정한 한도는 즉시 실제 적용값으로 반환된다")
    void 연결된_부모가_한도를_설정한다() {
        given(memberMapper.selectActiveParentByChildId(2L)).willReturn(parent(1L));
        given(permissionMapper.updateParentMonthlyLimit(1L, 2L, 3)).willReturn(1);
        given(permissionMapper.selectParentMonthlyLimit(2L)).willReturn(limit(3));
        given(teenyScoreMapper.selectTeenyScoreGradeByChildId(2L)).willReturn(grade(5));
        given(permissionMapper.countCreatedAtThisMonth(2L)).willReturn(2);

        PermissionLimitResponseDTO result = service.updateMonthlyLimit(
                1L, "PARENT", 2L, new PermissionLimitUpdateRequestDTO(3));

        assertThat(result.getParentConfiguredLimit()).isEqualTo(3);
        assertThat(result.getEffectiveLimit()).isEqualTo(3);
        assertThat(result.getRemainingDays()).isEqualTo(1);
        assertThat(result.isCustomizedByParent()).isTrue();
    }

    @Test
    @DisplayName("부모 설정 한도에 도달한 자녀의 새로운 날짜 요청을 차단한다")
    void 부모_설정_한도가_요청_생성에_적용된다() {
        given(permissionMapper.selectParentMonthlyLimit(2L)).willReturn(limit(2));
        given(permissionMapper.countCreatedAtThisMonth(2L)).willReturn(2);
        given(permissionMapper.selectCreatedTodayByChildId(2L)).willReturn(List.of());
        PermissionRequestDTO request = PermissionRequestDTO.builder()
                .categories(List.of(1L)).reason("사유").build();

        assertThatThrownBy(() -> service.createPermission(2L, "CHILD", request))
                .isInstanceOf(BusinessException.class);

        verify(teenyScoreMapper, never()).selectTeenyScoreGradeByChildId(any());
        verify(permissionMapper, never()).insertPermission(any());
    }

    @Test
    @DisplayName("오늘만 허용 현황의 남은 일수도 부모 설정 한도로 계산한다")
    void 부모_설정_한도가_현황_조회에_적용된다() {
        given(permissionMapper.selectParentMonthlyLimit(2L)).willReturn(limit(4));
        given(permissionMapper.countCreatedAtThisMonth(2L)).willReturn(2);
        given(permissionMapper.selectCreatedTodayByChildId(2L)).willReturn(List.of());

        PermissionStatusResponseDTO result =
                service.getPermissionStatus(2L, "CHILD", null);

        assertThat(result.getMonthlyRemainingCount()).isEqualTo(2);
        verify(teenyScoreMapper, never()).selectTeenyScoreGradeByChildId(any());
    }

    @Test
    @DisplayName("자녀 계정은 부모 설정 한도를 변경할 수 없다")
    void 자녀는_한도를_변경할_수_없다() {
        assertThatThrownBy(() -> service.updateMonthlyLimit(
                2L, "CHILD", 2L, new PermissionLimitUpdateRequestDTO(3)))
                .isInstanceOf(BusinessException.class);

        verify(permissionMapper, never()).updateParentMonthlyLimit(any(), any(), any());
    }

    @Test
    @DisplayName("다른 부모는 자녀의 설정 한도를 변경할 수 없다")
    void 다른_부모는_한도를_변경할_수_없다() {
        given(memberMapper.selectActiveParentByChildId(2L)).willReturn(parent(9L));

        assertThatThrownBy(() -> service.updateMonthlyLimit(
                1L, "PARENT", 2L, new PermissionLimitUpdateRequestDTO(3)))
                .isInstanceOf(BusinessException.class);

        verify(permissionMapper, never()).updateParentMonthlyLimit(any(), any(), any());
    }

    private MemberParentVO parent(Long id) {
        MemberParentVO parent = new MemberParentVO();
        parent.setParentId(id);
        return parent;
    }

    private TeenyScoreGradeVO grade(int value) {
        TeenyScoreGradeVO grade = new TeenyScoreGradeVO();
        grade.setMonthlyOverrideLimit(value);
        return grade;
    }

    private PermissionLimitVO limit(int value) {
        PermissionLimitVO limit = new PermissionLimitVO();
        limit.setMonthlyPermissionDayLimit(value);
        return limit;
    }
}
