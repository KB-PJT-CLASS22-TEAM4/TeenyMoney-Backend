package com.teenyfin.teenymoney.domain.family.service;

import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberParentVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.exception.CommonErrorCode;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("FamilyAccessService - 자녀 소유권 검증")
class FamilyAccessServiceTest {

    private MemberMapper memberMapper;
    private FamilyAccessService familyAccessService;

    @BeforeEach
    void setUp() {
        memberMapper = mock(MemberMapper.class);
        familyAccessService = new FamilyAccessService(memberMapper);
    }

    private static MemberParentVO parentVO(long parentId) {
        MemberParentVO parent = new MemberParentVO();
        parent.setParentId(parentId);
        return parent;
    }

    private static void expectForbidden(String situation, ThrowingCallable call) {
        BusinessException thrown = catchThrowableOfType(call, BusinessException.class);

        String actual = (thrown == null)
                ? "거부되지 않고 통과함"
                : "거부됨 (" + thrown.getErrorCode().getCode() + ")";
        System.out.printf("    입력: %s%n    기대: 거부됨 (AUTH_FORBIDDEN)%n    실제: %s%n%n",
                situation, actual);

        assertThat(thrown).as("거부되어야 하는데 통과했다: %s", situation).isNotNull();
        assertThat(thrown.getErrorCode()).isEqualTo(CommonErrorCode.AUTH_FORBIDDEN);
    }

    @Test
    @DisplayName("자녀 본인 -> 통과, DB를 보지 않는다")
    void childAccessingOwnDataPasses() {
        assertThatCode(() -> familyAccessService.requireChildAccess(
                new MemberPrincipal(2L, "CHILD"), 2L)).doesNotThrowAnyException();

        System.out.printf("    입력: 자녀 2가 자기 데이터 요청%n"
                + "    기대: 통과, 조회 없음%n    실제: 통과%n%n");

        // 토큰의 id와 대상 id가 같으면 관계를 볼 필요가 없다.
        verifyNoInteractions(memberMapper);
    }

    @Test
    @DisplayName("자녀가 남의 자녀 id -> 거부, DB를 보지 않는다")
    void childAccessingAnotherChildIsForbidden() {
        expectForbidden("자녀 2가 자녀 3의 데이터 요청",
                () -> familyAccessService.requireChildAccess(
                        new MemberPrincipal(2L, "CHILD"), 3L));

        verifyNoInteractions(memberMapper);
    }

    @Test
    @DisplayName("연결된 부모 -> 통과")
    void parentAccessingOwnChildPasses() {
        when(memberMapper.selectActiveParentByChildId(2L)).thenReturn(parentVO(1L));

        assertThatCode(() -> familyAccessService.requireChildAccess(
                new MemberPrincipal(1L, "PARENT"), 2L)).doesNotThrowAnyException();

        System.out.printf("    입력: 부모 1이 자기 자녀 2 요청%n"
                + "    기대: 통과%n    실제: 통과%n%n");
    }

    @Test
    @DisplayName("타 가족 부모 -> 거부")
    void parentAccessingAnotherFamilysChildIsForbidden() {
        // 자녀 2의 부모는 1이다. 요청자는 9다.
        when(memberMapper.selectActiveParentByChildId(2L)).thenReturn(parentVO(1L));

        // 정상 토큰에 hasRole('PARENT')도 통과하는 요청이다. 여기서만 걸린다.
        expectForbidden("부모 9가 남의 자녀 2를 URL에 넣어 요청",
                () -> familyAccessService.requireChildAccess(
                        new MemberPrincipal(9L, "PARENT"), 2L));
    }

    @Test
    @DisplayName("아직 연동되지 않은 자녀 -> 거부 (NPE가 아니라)")
    void parentAccessingUnlinkedChildIsForbidden() {
        when(memberMapper.selectActiveParentByChildId(2L)).thenReturn(null);

        expectForbidden("부모 1이 아무와도 연동되지 않은 자녀 2를 요청",
                () -> familyAccessService.requireChildAccess(
                        new MemberPrincipal(1L, "PARENT"), 2L));
    }

    @Test
    @DisplayName("principal이 null -> 거부, DB를 보지 않는다")
    void nullPrincipalIsForbidden() {
        expectForbidden("인증 정보 없음", () ->
                familyAccessService.requireChildAccess(null, 2L));

        verifyNoInteractions(memberMapper);
    }

    @Test
    @DisplayName("childId가 null -> 거부, DB를 보지 않는다")
    void nullChildIdIsForbidden() {
        expectForbidden("대상 자녀 id 없음", () ->
                familyAccessService.requireChildAccess(
                        new MemberPrincipal(1L, "PARENT"), null));

        verifyNoInteractions(memberMapper);
    }

    @Test
    @DisplayName("알 수 없는 role -> 거부")
    void unknownRoleIsForbidden() {
        expectForbidden("role = ADMIN 같은 미정의 역할",
                () -> familyAccessService.requireChildAccess(
                        new MemberPrincipal(1L, "ADMIN"), 2L));
    }
}
