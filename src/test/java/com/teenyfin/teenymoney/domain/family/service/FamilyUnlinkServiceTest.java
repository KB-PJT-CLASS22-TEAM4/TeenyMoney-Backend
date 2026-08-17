package com.teenyfin.teenymoney.domain.family.service;

import com.teenyfin.teenymoney.domain.allowance.mapper.AllowanceScheduleMapper;
import com.teenyfin.teenymoney.domain.allowance.vo.AllowanceScheduleVO;
import com.teenyfin.teenymoney.domain.family.exception.FamilyErrorCode;
import com.teenyfin.teenymoney.domain.family.mapper.FamilyConnectionMapper;
import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductEnrollmentVO;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import com.teenyfin.teenymoney.domain.notification.service.NotificationService;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;
import com.teenyfin.teenymoney.domain.quest.mapper.QuestMapper;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.exception.CommonErrorCode;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("가족 연동 해제")
class FamilyUnlinkServiceTest {

    private static final Long PARENT_ID = 1L;
    private static final Long CHILD_ID = 2L;
    private static final Long OTHER_CHILD_ID = 3L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 17, 10, 0);

    private final FamilyAccessService familyAccessService = mock(FamilyAccessService.class);
    private final FamilyConnectionMapper familyConnectionMapper = mock(FamilyConnectionMapper.class);
    private final QuestMapper questMapper = mock(QuestMapper.class);
    private final AllowanceScheduleMapper allowanceScheduleMapper = mock(AllowanceScheduleMapper.class);
    private final FinancialProductMapper financialProductMapper = mock(FinancialProductMapper.class);
    private final MemberMapper memberMapper = mock(MemberMapper.class);
    private final NotificationService notificationService = mock(NotificationService.class);

    private FamilyUnlinkService service;

    @BeforeEach
    void setUp() {
        service = new FamilyUnlinkService(
                familyAccessService,
                familyConnectionMapper,
                questMapper,
                allowanceScheduleMapper,
                financialProductMapper,
                memberMapper,
                notificationService,
                Clock.fixed(Instant.parse("2026-08-17T01:00:00Z"), ZoneId.of("Asia/Seoul")));

        given(familyConnectionMapper.deactivate(eq(PARENT_ID), eq(CHILD_ID), any())).willReturn(1);
        MemberVO parent = new MemberVO();
        parent.setId(PARENT_ID);
        parent.setName("김부모");
        given(memberMapper.selectById(PARENT_ID)).willReturn(parent);
    }

    @Test
    @DisplayName("해제하면 연결을 끊고 자녀에게 알린다")
    void deactivatesConnectionAndNotifiesChild() {
        service.unlink(parent(), CHILD_ID);

        verify(familyConnectionMapper).deactivate(PARENT_ID, CHILD_ID, NOW);
        verify(notificationService).createNotification(
                eq(CHILD_ID),
                eq("부모와 연결이 해제됐어요"),
                eq("김부모 · 새 연동 코드로 다시 연결할 수 있어요"),
                eq(NotificationReferenceType.CONNECTION),
                eq(PARENT_ID),
                eq(true));
    }

    @Test
    @DisplayName("진행 중인 퀘스트를 마감한다")
    void expiresOpenQuests() {
        service.unlink(parent(), CHILD_ID);

        verify(questMapper).expireOpenQuestsByParentAndChild(PARENT_ID, CHILD_ID, NOW);
    }

    @Test
    @DisplayName("그 자녀의 정기 용돈 스케줄만 중지한다")
    void deactivatesOnlyThatChildsAllowanceSchedule() {
        given(allowanceScheduleMapper.selectByParentId(PARENT_ID)).willReturn(List.of(
                schedule(10L, CHILD_ID, LocalDate.of(2026, 9, 5)),
                schedule(11L, OTHER_CHILD_ID, LocalDate.of(2026, 9, 5))));

        service.unlink(parent(), CHILD_ID);

        // 배치가 계속 돌면 끊긴 부모 지갑에서 실제로 송금이 나간다.
        verify(allowanceScheduleMapper)
                .updateActiveAndNextPaymentDate(10L, false, LocalDate.of(2026, 9, 5));
        verify(allowanceScheduleMapper, never())
                .updateActiveAndNextPaymentDate(eq(11L), any(Boolean.class), any());
    }

    @Test
    @DisplayName("이미 중지된 스케줄은 다시 건드리지 않는다")
    void skipsAlreadyInactiveSchedule() {
        AllowanceScheduleVO inactive = schedule(10L, CHILD_ID, LocalDate.of(2026, 9, 5));
        inactive.setActive(false);
        given(allowanceScheduleMapper.selectByParentId(PARENT_ID)).willReturn(List.of(inactive));

        service.unlink(parent(), CHILD_ID);

        verify(allowanceScheduleMapper, never())
                .updateActiveAndNextPaymentDate(any(), any(Boolean.class), any());
    }

    @Test
    @DisplayName("미상환 대출이 있으면 해제할 수 없고 아무것도 바꾸지 않는다")
    void rejectsUnlinkWhenLoanIsOutstanding() {
        given(financialProductMapper.selectLoanEnrollmentsByChildId(CHILD_ID))
                .willReturn(List.of(loan("ACTIVE")));

        assertError(() -> service.unlink(parent(), CHILD_ID),
                FamilyErrorCode.FAMILY_UNLINK_LOAN_OUTSTANDING);

        verify(familyConnectionMapper, never()).deactivate(any(), any(), any());
        verify(questMapper, never()).expireOpenQuestsByParentAndChild(any(), any(), any());
        verify(notificationService, never())
                .createNotification(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("연체 중인 대출도 미상환으로 본다")
    void treatsOverdueLoanAsOutstanding() {
        given(financialProductMapper.selectLoanEnrollmentsByChildId(CHILD_ID))
                .willReturn(List.of(loan("OVERDUE")));

        assertError(() -> service.unlink(parent(), CHILD_ID),
                FamilyErrorCode.FAMILY_UNLINK_LOAN_OUTSTANDING);
    }

    @Test
    @DisplayName("끝난 대출은 해제를 막지 않는다")
    void finishedLoanDoesNotBlockUnlink() {
        given(financialProductMapper.selectLoanEnrollmentsByChildId(CHILD_ID))
                .willReturn(List.of(loan("REPAID"), loan("REJECTED"), loan("PENDING")));

        service.unlink(parent(), CHILD_ID);

        verify(familyConnectionMapper).deactivate(PARENT_ID, CHILD_ID, NOW);
    }

    @Test
    @DisplayName("이미 해제된 관계는 409다")
    void alreadyUnlinkedConnectionIsConflict() {
        given(familyConnectionMapper.deactivate(eq(PARENT_ID), eq(CHILD_ID), any())).willReturn(0);

        assertError(() -> service.unlink(parent(), CHILD_ID),
                FamilyErrorCode.FAMILY_NOT_LINKED);

        verify(notificationService, never())
                .createNotification(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("내 자녀가 아니면 아무것도 조회하지 않고 403이다")
    void otherFamilysChildIsForbidden() {
        willThrow(new BusinessException(CommonErrorCode.AUTH_FORBIDDEN))
                .given(familyAccessService).requireChildAccess(any(), eq(CHILD_ID));

        assertThatThrownBy(() -> service.unlink(parent(), CHILD_ID))
                .isInstanceOf(BusinessException.class);

        verify(financialProductMapper, never()).selectLoanEnrollmentsByChildId(any());
        verify(familyConnectionMapper, never()).deactivate(any(), any(), any());
    }

    private MemberPrincipal parent() {
        return new MemberPrincipal(PARENT_ID, "PARENT");
    }

    private AllowanceScheduleVO schedule(Long id, Long childId, LocalDate nextPaymentDate) {
        AllowanceScheduleVO schedule = new AllowanceScheduleVO();
        schedule.setId(id);
        schedule.setParentId(PARENT_ID);
        schedule.setChildId(childId);
        schedule.setNextPaymentDate(nextPaymentDate);
        schedule.setActive(true);
        return schedule;
    }

    private FinancialProductEnrollmentVO loan(String status) {
        FinancialProductEnrollmentVO enrollment = new FinancialProductEnrollmentVO();
        enrollment.setStatus(status);
        return enrollment;
    }

    private void assertError(Runnable call, FamilyErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
