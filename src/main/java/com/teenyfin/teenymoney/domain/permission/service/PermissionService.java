package com.teenyfin.teenymoney.domain.permission.service;

import com.teenyfin.teenymoney.domain.categoryPolicy.exception.CategoryPolicyErrorCode;
import com.teenyfin.teenymoney.domain.categoryPolicy.mapper.CategoryPolicyMapper;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import com.teenyfin.teenymoney.domain.notification.service.NotificationService;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;
import com.teenyfin.teenymoney.domain.categoryPolicy.vo.CategoryPolicyVO;
import com.teenyfin.teenymoney.domain.permission.dto.request.PermissionRequestDTO;
import com.teenyfin.teenymoney.domain.permission.dto.request.PermissionLimitUpdateRequestDTO;
import com.teenyfin.teenymoney.domain.permission.dto.request.PermissionUpdateRequestDTO;
import com.teenyfin.teenymoney.domain.permission.dto.response.PermissionCategoryStatusResponseDTO;
import com.teenyfin.teenymoney.domain.permission.dto.response.PermissionResponseDTO;
import com.teenyfin.teenymoney.domain.permission.dto.response.PermissionLimitResponseDTO;
import com.teenyfin.teenymoney.domain.permission.dto.response.PermissionStatusResponseDTO;
import com.teenyfin.teenymoney.domain.permission.exception.PermissionErrorCode;
import com.teenyfin.teenymoney.domain.permission.mapper.PermissionMapper;
import com.teenyfin.teenymoney.domain.permission.vo.PermissionInsertVO;
import com.teenyfin.teenymoney.domain.permission.vo.PermissionLimitVO;
import com.teenyfin.teenymoney.domain.permission.vo.PermissionStatus;
import com.teenyfin.teenymoney.domain.permission.vo.PermissionVO;
import com.teenyfin.teenymoney.domain.teenyscore.mapper.TeenyScoreMapper;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionMapper permissionMapper;
    private final MemberMapper memberMapper;
    private final TeenyScoreMapper teenyScoreMapper;
    private final CategoryPolicyMapper categoryPolicyMapper;

    private final NotificationService notificationService;

    // 오늘 날짜에 생성된 오늘만 허용 요청 조회
    @Transactional(readOnly = true)
    public List<PermissionResponseDTO> getPermission(Long memberId, String role, Long childId) {

        if (role.equals("CHILD")) {
            childId = memberId;
        } else if (childId == null) {   // 부모의 경우 childId 값은 필수
            throw new BusinessException(CategoryPolicyErrorCode.CHILD_ID_REQUIRED);
        } else if (!Objects.equals(memberMapper.selectActiveParentByChildId(childId).getParentId(), memberId)) {  // 해당 자녀와 연결된 부모인지 확인
            throw new BusinessException(CategoryPolicyErrorCode.FORBIDDEN_TO_CHILD);
        }

        List<PermissionVO> permissionVOList = permissionMapper.selectCreatedTodayByChildId(childId);

        return permissionVOList.stream()
                .map(x -> PermissionResponseDTO.builder()
                        .id(x.getId())
                        .category(x.getCategory())
                        .reason(x.getReason())
                        .status(x.getStatus())
                        .createdAt(x.getCreatedAt())
                        .build())
                .toList();
    }

    // 이번 달 오늘만 허용 사용 현황과 카테고리별 오늘 기준 현재 상태 조회
    @Transactional(readOnly = true)
    public PermissionStatusResponseDTO getPermissionStatus(Long memberId, String role, Long childId) {

        if (role.equals("CHILD")) {
            childId = memberId;
        } else if (childId == null) {   // 부모의 경우 childId 값은 필수
            throw new BusinessException(CategoryPolicyErrorCode.CHILD_ID_REQUIRED);
        } else if (!Objects.equals(memberMapper.selectActiveParentByChildId(childId).getParentId(), memberId)) {  // 해당 자녀와 연결된 부모인지 확인
            throw new BusinessException(CategoryPolicyErrorCode.FORBIDDEN_TO_CHILD);
        }

        int usedCount = permissionMapper.countCreatedAtThisMonth(childId); // 이번 달에 오늘만 허용을 요청한 일수 (오늘 포함)
        int monthlyLimit = effectiveMonthlyLimit(childId); // 부모 설정값이 있으면 우선하고, 없으면 등급 기본값 사용
        int remainingCount = Math.max(monthlyLimit - usedCount, 0);

        // 오늘 카테고리별로 이미 생성된 요청의 상태 (없으면 AVAILABLE)
        Map<Long, PermissionStatus> statusByCategoryId = permissionMapper.selectCreatedTodayByChildId(childId).stream()
                .collect(Collectors.toMap(PermissionVO::getCategoryId, PermissionVO::getStatus));

        List<CategoryPolicyVO> categoryPolicyVOList = categoryPolicyMapper.selectByChildId(childId);

        List<PermissionCategoryStatusResponseDTO> categories = categoryPolicyVOList.stream()
                .map(x -> PermissionCategoryStatusResponseDTO.builder()
                        .categoryId(x.getCategoryId())
                        .categoryName(x.getCategoryName())
                        .policy(x.getPolicy())
                        .status(statusByCategoryId.getOrDefault(x.getCategoryId(), PermissionStatus.AVAILABLE))
                        .build())
                .toList();

        return PermissionStatusResponseDTO.builder()
                .monthlyUsedCount(usedCount)
                .monthlyRemainingCount(remainingCount)
                .categories(categories)
                .build();
    }

    @Transactional(readOnly = true)
    public PermissionLimitResponseDTO getMonthlyLimit(
            Long parentId, String role, Long childId) {
        requireParentChildAccess(parentId, role, childId);
        return monthlyLimitResponse(childId);
    }

    @Transactional
    public PermissionLimitResponseDTO updateMonthlyLimit(
            Long parentId, String role, Long childId,
            PermissionLimitUpdateRequestDTO request) {
        requireParentChildAccess(parentId, role, childId);
        int updated = permissionMapper.updateParentMonthlyLimit(
                parentId, childId, request.getMonthlyAllowedDays());
        if (updated != 1) {
            throw new BusinessException(CategoryPolicyErrorCode.FORBIDDEN_TO_CHILD);
        }
        return monthlyLimitResponse(childId);
    }

    // 새로운 오늘만 허용 요청 생성
    @Transactional
    public List<PermissionResponseDTO> createPermission(Long memberId, String role, PermissionRequestDTO permissionRequestDTO) {

        // 자녀만 오늘만 요청 생성 가능
        if (!role.equals("CHILD")) {
            throw new BusinessException(PermissionErrorCode.ONLY_CHILD_CAN_MANAGE_PERMISSION);
        }

        int count = permissionMapper.countCreatedAtThisMonth(memberId); // 이번 달에 오늘만 허용을 요청한 일수 (오늘 포함)
        int monthlyLimit = effectiveMonthlyLimit(memberId); // 날짜 단위 집계 정책은 유지하고 한도의 출처만 확장

        // 오늘 이미 요청한 적이 있으면 이번 요청은 새로운 날짜를 소모하지 않으므로 월간 한도 검사에서 제외한다
        boolean requestedToday = !permissionMapper.selectCreatedTodayByChildId(memberId).isEmpty();

        if (!requestedToday && count >= monthlyLimit) {
            throw new BusinessException(PermissionErrorCode.MONTHLY_LIMIT_EXCEEDED);
        }

        Long parentId = memberMapper.selectActiveParentByChildId(memberId).getParentId();
        List<Long> categories = permissionRequestDTO.getCategories();

        if (categories.isEmpty()) {
            return getPermission(memberId, role, null);
        }

        for (Long categoryId : categories) {
            PermissionInsertVO permissionInsertVO = PermissionInsertVO.builder()
                    .parentId(parentId)
                    .childId(memberId)
                    .categoryId(categoryId)
                    .reason(permissionRequestDTO.getReason())
                    .build();

            // 오늘만 요청 row 삽입 (카테고리 포함). 같은 날 같은 카테고리로 이미 요청한 적이 있으면
            // UQ_T_TDP_REQ_L_CHILD_CATEGORY_DATE 유니크 제약에 걸려 여기서 터진다.
            try {
                permissionMapper.insertPermission(permissionInsertVO);
            } catch (DuplicateKeyException e) {
                throw new BusinessException(PermissionErrorCode.DUPLICATE_TODAY_PERMISSION_REQUEST);
            }
        }

        MemberVO memberVO = memberMapper.selectById(memberId);

        // 부모에게 알림 발송
        String title = "자녀가 오늘만 허용을 요청했어요";
        String content = memberVO.getName() + " · " + categoryPolicyMapper.selectCategoryNameById(categories.get(0));

        if (categories.size() > 1) {
            content += " 외 " + (categories.size() - 1) + "건";
        }

        notificationService.createNotification(parentId, title, content, NotificationReferenceType.TODAY_PERMISSION, null, true);

        return getPermission(memberId, role, null);
    }

    // 오늘 날짜에 생성한 오늘만 허용 요청 수정
    @Transactional
    public List<PermissionResponseDTO> updatePermission(Long memberId, String role, Long permissionId, PermissionUpdateRequestDTO permissionUpdateRequestDTO) {

        // 자녀만 오늘만 요청 수정 가능
        if (!role.equals("CHILD")) {
            throw new BusinessException(PermissionErrorCode.ONLY_CHILD_CAN_MANAGE_PERMISSION);
        }

        PermissionVO permissionVO = permissionMapper.selectById(permissionId);
        validatePermission(memberId, role, permissionVO);

        // 사유 수정
        permissionMapper.updatePermissionReason(permissionId, permissionUpdateRequestDTO.getReason());

        return getPermission(memberId, role, null);
    }

    // 오늘 날짜에 생성한 오늘만 허용 요청 승인
    @Transactional
    public List<PermissionResponseDTO> approvePermission(Long memberId, String role, Long permissionId) {

        // 부모만 오늘만 요청 승인 가능
        if (!role.equals("PARENT")) {
            throw new BusinessException(PermissionErrorCode.ONLY_PARENT_CAN_REVIEW_PERMISSION);
        }

        PermissionVO permissionVO = permissionMapper.selectById(permissionId);
        validatePermission(memberId, role, permissionVO);

        // 상태 변경
        permissionMapper.updatePermissionStatus(permissionId, PermissionStatus.APPROVED);

        // 자녀에게 알림 발송
        String title = "오늘만 허용이 승인되었어요";
        String content = permissionVO.getCategory();

        notificationService.createNotification(permissionVO.getChildId(), title, content, NotificationReferenceType.TODAY_PERMISSION, null, true);


        return getPermission(memberId, role, permissionVO.getChildId());
    }

    // 오늘 날짜에 생성한 오늘만 허용 요청 거절
    @Transactional
    public List<PermissionResponseDTO> rejectPermission(Long memberId, String role, Long permissionId) {

        // 부모만 오늘만 요청 거절 가능
        if (!role.equals("PARENT")) {
            throw new BusinessException(PermissionErrorCode.ONLY_PARENT_CAN_REVIEW_PERMISSION);
        }

        PermissionVO permissionVO = permissionMapper.selectById(permissionId);
        validatePermission(memberId, role, permissionVO);

        // 상태 변경
        permissionMapper.updatePermissionStatus(permissionId, PermissionStatus.REJECTED);

        // 자녀에게 알림 발송
        String title = "오늘만 허용이 거절되었어요";
        String content = permissionVO.getCategory();

        notificationService.createNotification(permissionVO.getChildId(), title, content, NotificationReferenceType.TODAY_PERMISSION, null, true);

        return getPermission(memberId, role, permissionVO.getChildId());
    }

    // 오늘 날짜에 생성한 오늘만 허용 요청 삭제
    @Transactional
    public void deletePermission(Long memberId, String role, Long permissionId) {

        // 자녀만 오늘만 요청 삭제 가능
        if (!role.equals("CHILD")) {
            throw new BusinessException(PermissionErrorCode.ONLY_CHILD_CAN_MANAGE_PERMISSION);
        }

        PermissionVO permissionVO = permissionMapper.selectById(permissionId);
        validatePermission(memberId, role, permissionVO);

        // 오늘만 허용 요청 row 삭제
        permissionMapper.deletePermissionById(permissionId);
    }

    // 부모 대상의 오늘만 허용 요청 유효성 검사
    private void validatePermission(Long memberId, String role, PermissionVO permissionVO) {

        // 해당하는 아이디의 오늘만 허용 요청이 없을 경우 예외 처리
        if (permissionVO == null) {
            throw new BusinessException(PermissionErrorCode.INVALID_PERMISSION_ID);
        }

        // 자신이 생성했거나 자신에게 요청된 오늘만 허용 요청만 처리 가능
        if ((role.equals("CHILD") && !Objects.equals(permissionVO.getChildId(), memberId)) ||
                (role.equals("PARENT") && !Objects.equals(permissionVO.getParentId(), memberId))) {
            throw new BusinessException(PermissionErrorCode.FORBIDDEN_TO_PROCESS_PERMISSION);
        }

        // 오늘 날짜에 생성된 오늘만 허용 요청만 처리 가능
        if (!permissionVO.getCreatedAt().toLocalDate().equals(LocalDate.now())) {
            throw new BusinessException(PermissionErrorCode.ONLY_CAN_PROCESS_PERMISSION_CREATED_TODAY);
        }

        // 대기 상태의 오늘만 허용 요청만 처리 가능
        if (permissionVO.getStatus() != PermissionStatus.PENDING) {
            throw new BusinessException(PermissionErrorCode.ONLY_CAN_PROCESS_PENDING_PERMISSION);
        }
    }

    /** 부모 설정값이 아직 없을 때만 기존 티니등급 한도를 사용한다. */
    private int effectiveMonthlyLimit(Long childId) {
        PermissionLimitVO limit = permissionMapper.selectParentMonthlyLimit(childId);
        if (limit != null && limit.getMonthlyPermissionDayLimit() != null) {
            return limit.getMonthlyPermissionDayLimit();
        }
        return gradeMonthlyLimit(childId);
    }

    private PermissionLimitResponseDTO monthlyLimitResponse(Long childId) {
        PermissionLimitVO limit = permissionMapper.selectParentMonthlyLimit(childId);
        Integer configured = limit == null ? null : limit.getMonthlyPermissionDayLimit();
        int gradeDefault = gradeMonthlyLimit(childId);
        int effective = configured == null ? gradeDefault : configured;
        int usedDays = permissionMapper.countCreatedAtThisMonth(childId);
        return PermissionLimitResponseDTO.builder()
                .childId(childId)
                .gradeDefaultLimit(gradeDefault)
                .parentConfiguredLimit(configured)
                .effectiveLimit(effective)
                .usedDays(usedDays)
                .remainingDays(Math.max(effective - usedDays, 0))
                .customizedByParent(configured != null)
                .build();
    }

    private int gradeMonthlyLimit(Long childId) {
        return teenyScoreMapper.selectTeenyScoreGradeByChildId(childId)
                .getMonthlyOverrideLimit();
    }

    private void requireParentChildAccess(Long parentId, String role, Long childId) {
        if (!"PARENT".equals(role)) {
            throw new BusinessException(
                    PermissionErrorCode.ONLY_PARENT_CAN_MANAGE_PERMISSION_LIMIT);
        }
        if (childId == null) {
            throw new BusinessException(CategoryPolicyErrorCode.CHILD_ID_REQUIRED);
        }
        var linkedParent = memberMapper.selectActiveParentByChildId(childId);
        if (linkedParent == null || !Objects.equals(linkedParent.getParentId(), parentId)) {
            throw new BusinessException(CategoryPolicyErrorCode.FORBIDDEN_TO_CHILD);
        }
    }
}
