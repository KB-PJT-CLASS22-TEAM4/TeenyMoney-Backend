package com.teenyfin.teenymoney.domain.permission.service;

import com.teenyfin.teenymoney.domain.categoryPolicy.exception.CategoryPolicyErrorCode;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import com.teenyfin.teenymoney.domain.permission.dto.request.PermissionRequestDTO;
import com.teenyfin.teenymoney.domain.permission.dto.response.PermissionResponseChildDTO;
import com.teenyfin.teenymoney.domain.permission.dto.response.PermissionResponseDTO;
import com.teenyfin.teenymoney.domain.permission.dto.response.PermissionResponseWrapperDTO;
import com.teenyfin.teenymoney.domain.permission.exception.PermissionErrorCode;
import com.teenyfin.teenymoney.domain.permission.mapper.PermissionMapper;
import com.teenyfin.teenymoney.domain.permission.vo.PermissionInsertVO;
import com.teenyfin.teenymoney.domain.permission.vo.PermissionVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.storage.S3Storage;
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
    private final S3Storage s3Storage;

    // 오늘 날짜에 생성된 오늘만 허용 요청 조회
    @Transactional(readOnly = true)
    public PermissionResponseWrapperDTO getPermission(Long memberId, String role) {

        PermissionVO permissionVO = switch (role) {
            case "PARENT" -> permissionMapper.selectCreatedTodayByParentId(memberId);
            case "CHILD" -> permissionMapper.selectCreatedTodayByChildId(memberId);
            default -> throw new BusinessException(CategoryPolicyErrorCode.INVALID_ROLE); // 추후 MemberErrorCode 추가 시 변경
        };

        // 오늘만 허용 요청이 없는 경우
        if (permissionVO == null) {
            return PermissionResponseWrapperDTO.builder()
                    .isExist(false)
                    .permission(null)
                    .build();
        }

        // 자녀 정보
        MemberVO childVO = memberMapper.selectById(permissionVO.getChildId());

        // 카테고리 이름 리스트로 변환
        List<String> categoryNameList = permissionMapper.selectPermissionCategoriesByPermissionId(permissionVO.getId());

        PermissionResponseDTO permissionResponseDTO = PermissionResponseDTO.builder()
                .id(permissionVO.getId())
                .child(PermissionResponseChildDTO.builder()
                        .id(childVO.getId())
                        .name(childVO.getName())
                        .profileImageUrl(s3Storage.presignedUrl(childVO.getProfileImageKey()))
                        .build())
                .categories(categoryNameList)
                .reason(permissionVO.getReason())
                .status(permissionVO.getStatus())
                .createdAt(permissionVO.getCreatedAt())
                .build();

        return PermissionResponseWrapperDTO.builder()
                .isExist(true)
                .permission(permissionResponseDTO)
                .build();
    }

    // 새로운 오늘만 허용 요청 생성
    @Transactional
    public PermissionResponseWrapperDTO createPermission(Long memberId, String role, PermissionRequestDTO permissionRequestDTO) {

        // 자녀만 오늘만 요청 생성 가능
        if (!role.equals("CHILD")) {
            throw new BusinessException(PermissionErrorCode.ONLY_CHILD_CAN_MANAGE_PERMISSION);
        }

        PermissionVO permissionVO = permissionMapper.selectCreatedTodayByChildId(memberId);

        // 이미 오늘 날짜에 생성된 오늘만 요청이 있을 경우 추가 생성 불가
        if (permissionVO != null) {
            throw new BusinessException(PermissionErrorCode.ALREADY_EXIST_TODAY_PERMISSION);
        }

        Long parentId = permissionMapper.selectParentIdByChildId(memberId);

        PermissionInsertVO permissionInsertVO = PermissionInsertVO.builder()
                .parentId(parentId)
                .childId(memberId)
                .reason(permissionRequestDTO.getReason())
                .build();

        // 오늘만 요청 row 삽입
        permissionMapper.insertPermission(permissionInsertVO);

        // 오늘만 허용 대상 카테고리 row 삽입
        permissionMapper.insertPermissionCategories(permissionInsertVO.getId(), permissionRequestDTO.getCategories());

        return getPermission(memberId, role);
    }

    // 오늘 날짜에 생성한 오늘만 허용 요청 수정
    @Transactional
    public PermissionResponseWrapperDTO updatePermission(Long memberId, String role, Long permissionId, PermissionRequestDTO permissionRequestDTO) {

        // 자녀만 오늘만 요청 수정 가능
        if (!role.equals("CHILD")) {
            throw new BusinessException(PermissionErrorCode.ONLY_CHILD_CAN_MANAGE_PERMISSION);
        }

        PermissionVO permissionVO = permissionMapper.selectById(permissionId);
        validatePermission(memberId, role, permissionVO);

        // 사유 수정
        permissionMapper.updatePermissionReason(permissionId, permissionRequestDTO.getReason());

        // 오늘만 허용 대상 카테고리 row 일괄 삭제
        permissionMapper.deletePermissionCategoriesByPermissionId(permissionId);

        // 오늘만 허용 대상 카테고리 row 새로 삽입
        permissionMapper.insertPermissionCategories(permissionId, permissionRequestDTO.getCategories());

        return getPermission(memberId, role);
    }

    // 오늘 날짜에 생성한 오늘만 허용 요청 승인
    @Transactional
    public PermissionResponseWrapperDTO approvePermission(Long memberId, String role, Long permissionId) {

        // 부모만 오늘만 요청 승인 가능
        if (!role.equals("PARENT")) {
            throw new BusinessException(PermissionErrorCode.ONLY_PARENT_CAN_REVIEW_PERMISSION);
        }

        PermissionVO permissionVO = permissionMapper.selectById(permissionId);
        validatePermission(memberId, role, permissionVO);

        // 상태 변경
        permissionMapper.updatePermissionStatus(permissionId, "APPROVED");

        return getPermission(memberId, role);
    }

    // 오늘 날짜에 생성한 오늘만 허용 요청 거절
    @Transactional
    public PermissionResponseWrapperDTO rejectPermission(Long memberId, String role, Long permissionId) {

        // 부모만 오늘만 요청 거절 가능
        if (!role.equals("PARENT")) {
            throw new BusinessException(PermissionErrorCode.ONLY_PARENT_CAN_REVIEW_PERMISSION);
        }

        PermissionVO permissionVO = permissionMapper.selectById(permissionId);
        validatePermission(memberId, role, permissionVO);

        // 상태 변경
        permissionMapper.updatePermissionStatus(permissionId, "REJECTED");

        return getPermission(memberId, role);
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
