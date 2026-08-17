package com.teenyfin.teenymoney.domain.family.service;

import com.teenyfin.teenymoney.domain.allowance.mapper.AllowanceScheduleMapper;
import com.teenyfin.teenymoney.domain.allowance.vo.AllowanceScheduleVO;
import com.teenyfin.teenymoney.domain.family.exception.FamilyErrorCode;
import com.teenyfin.teenymoney.domain.family.mapper.FamilyConnectionMapper;
import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductEnrollmentVO;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.notification.service.NotificationService;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;
import com.teenyfin.teenymoney.domain.quest.mapper.QuestMapper;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * 부모가 자녀와의 연동을 해제한다.
 *
 * 관계만 끊고 끝나지 않는다. 연결을 전제로 계속 돌아가는 것들을 함께 멈춰야 한다.
 * 기준은 두 가지다 — 돈이 도는가, 영원히 끝나지 않는가.
 *
 *   정기 용돈 스케줄  배치가 계속 돌아 끊긴 부모 지갑에서 실제로 송금한다      -> 중지
 *   진행 중인 퀘스트  승인할 부모가 없어 PENDING 이 남고, 승인되면 보상이 나간다 -> 마감
 *   카테고리 정책     남아도 무해하고 자녀 소비 제한이 유지되는 쪽이 안전하다    -> 그대로 둔다
 *
 * 오늘만 허용 PENDING 과 금융상품 가입 PENDING 도 승인할 부모가 없어지지만,
 * 돈이 움직이지 않고 타 도메인 매퍼를 건드려야 해서 이번 범위에서 제외한다.
 */
@Service
public class FamilyUnlinkService {

    private static final String UNLINKED_TITLE = "부모와 연결이 해제됐어요";

    /** 아직 돈을 돌려받지 못한 대출 상태. 이 상태면 해제를 막는다. */
    private static final Set<String> OUTSTANDING_LOAN_STATUSES = Set.of("ACTIVE", "OVERDUE");

    private final FamilyAccessService familyAccessService;
    private final FamilyConnectionMapper familyConnectionMapper;
    private final QuestMapper questMapper;
    private final AllowanceScheduleMapper allowanceScheduleMapper;
    private final FinancialProductMapper financialProductMapper;
    private final MemberMapper memberMapper;
    private final NotificationService notificationService;
    private final Clock clock;

    public FamilyUnlinkService(FamilyAccessService familyAccessService,
                               FamilyConnectionMapper familyConnectionMapper,
                               QuestMapper questMapper,
                               AllowanceScheduleMapper allowanceScheduleMapper,
                               FinancialProductMapper financialProductMapper,
                               MemberMapper memberMapper,
                               NotificationService notificationService,
                               Clock clock) {
        this.familyAccessService = familyAccessService;
        this.familyConnectionMapper = familyConnectionMapper;
        this.questMapper = questMapper;
        this.allowanceScheduleMapper = allowanceScheduleMapper;
        this.financialProductMapper = financialProductMapper;
        this.memberMapper = memberMapper;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    /**
     * 순서가 중요하다. 막을 이유가 있으면 아무것도 건드리기 전에 막고, 정리를 마친 뒤
     * 마지막에 관계를 끊는다. 한 트랜잭션이라 중간에 실패하면 전부 되돌아간다.
     */
    @Transactional
    public void unlink(MemberPrincipal principal, Long childId) {
        familyAccessService.requireChildAccess(principal, childId);

        Long parentId = principal.memberId();
        requireNoOutstandingLoan(childId);

        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        stopAllowanceSchedule(parentId, childId);
        questMapper.expireOpenQuestsByParentAndChild(parentId, childId, now);

        if (familyConnectionMapper.deactivate(parentId, childId, now) != 1) {
            throw new BusinessException(FamilyErrorCode.FAMILY_NOT_LINKED);
        }

        notifyChild(parentId, childId);
    }

    private void requireNoOutstandingLoan(Long childId) {
        List<FinancialProductEnrollmentVO> loans =
                financialProductMapper.selectLoanEnrollmentsByChildId(childId);
        boolean outstanding = loans.stream()
                .anyMatch(loan -> OUTSTANDING_LOAN_STATUSES.contains(loan.getStatus()));
        if (outstanding) {
            throw new BusinessException(FamilyErrorCode.FAMILY_UNLINK_LOAN_OUTSTANDING);
        }
    }

    /**
     * 스케줄은 UNIQUE(parent_id, child_id) 라 이 자녀 것은 최대 한 건이다.
     * 전용 조회를 새로 만들지 않고 부모 목록에서 골라낸다.
     */
    private void stopAllowanceSchedule(Long parentId, Long childId) {
        for (AllowanceScheduleVO schedule : allowanceScheduleMapper.selectByParentId(parentId)) {
            if (childId.equals(schedule.getChildId()) && schedule.isActive()) {
                // 지급일은 그대로 둔다. 재연결하면 부모가 다시 켜기만 하면 된다.
                allowanceScheduleMapper.updateActiveAndNextPaymentDate(
                        schedule.getId(), false, schedule.getNextPaymentDate());
            }
        }
    }

    /**
     * 부모에게는 보내지 않는다. 본인이 한 행동이다.
     * 이름을 제목이 아니라 내용 앞에 두는 형식은 PermissionService 와 같다.
     */
    private void notifyChild(Long parentId, Long childId) {
        String parentName = memberMapper.selectById(parentId).getName();
        notificationService.createNotification(
                childId,
                UNLINKED_TITLE,
                parentName + " · 새 연동 코드로 다시 연결할 수 있어요",
                NotificationReferenceType.CONNECTION,
                parentId,
                true);
    }
}
