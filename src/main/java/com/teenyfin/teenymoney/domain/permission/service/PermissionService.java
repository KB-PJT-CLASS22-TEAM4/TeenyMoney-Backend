package com.teenyfin.teenymoney.domain.permission.service;

import com.teenyfin.teenymoney.domain.categoryPolicy.exception.CategoryPolicyErrorCode;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.permission.dto.request.PermissionRequestDTO;
import com.teenyfin.teenymoney.domain.permission.dto.request.PermissionUpdateRequestDTO;
import com.teenyfin.teenymoney.domain.permission.dto.response.PermissionResponseDTO;
import com.teenyfin.teenymoney.domain.permission.exception.PermissionErrorCode;
import com.teenyfin.teenymoney.domain.permission.mapper.PermissionMapper;
import com.teenyfin.teenymoney.domain.permission.vo.PermissionInsertVO;
import com.teenyfin.teenymoney.domain.permission.vo.PermissionVO;
import com.teenyfin.teenymoney.domain.teenyscore.mapper.TeenyScoreMapper;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionMapper permissionMapper;
    private final MemberMapper memberMapper;
    private final TeenyScoreMapper teenyScoreMapper;

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

    // 새로운 오늘만 허용 요청 생성
    @Transactional
    public List<PermissionResponseDTO> createPermission(Long memberId, String role, PermissionRequestDTO permissionRequestDTO) {

        // 자녀만 오늘만 요청 생성 가능
        if (!role.equals("CHILD")) {
            throw new BusinessException(PermissionErrorCode.ONLY_CHILD_CAN_MANAGE_PERMISSION);
        }

        int count = permissionMapper.countCreatedAtThisMonth(memberId); // 이번 달에 오늘만 허용을 요청한 일수
        int monthlyLimit = teenyScoreMapper.selectTeenyScoreByChildId(memberId).getMonthlyOverrideLimit();  // 이번 달에 요청할 수 있는 일수

        if (count >= monthlyLimit) {
            throw new BusinessException(PermissionErrorCode.MONTHLY_LIMIT_EXCEEDED);
        }

        Long parentId = memberMapper.selectActiveParentByChildId(memberId).getParentId();

        for (Long categoryId : permissionRequestDTO.getCategories()) {
            PermissionInsertVO permissionInsertVO = PermissionInsertVO.builder()
                    .parentId(parentId)
                    .childId(memberId)
                    .reason(permissionRequestDTO.getReason())
                    .build();

            // 오늘만 요청 row 삽입
            permissionMapper.insertPermission(permissionInsertVO);

            // 오늘만 허용 대상 카테고리 row 삽입
            permissionMapper.insertPermissionCategory(permissionInsertVO.getId(), categoryId);
        }

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
        permissionMapper.updatePermissionStatus(permissionId, "APPROVED");

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
        permissionMapper.updatePermissionStatus(permissionId, "REJECTED");

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

        // 오늘만 허용 대상 카테고리 row 일괄 삭제
        permissionMapper.deletePermissionCategoriesByPermissionId(permissionId);

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
        if (!permissionVO.getStatus().equals("PENDING")) {
            throw new BusinessException(PermissionErrorCode.ONLY_CAN_PROCESS_PENDING_PERMISSION);
        }
    }
}
