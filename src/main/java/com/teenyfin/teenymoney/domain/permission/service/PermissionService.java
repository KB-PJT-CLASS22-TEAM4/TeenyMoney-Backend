package com.teenyfin.teenymoney.domain.permission.service;

import com.teenyfin.teenymoney.domain.categoryPolicy.exception.CategoryPolicyErrorCode;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import com.teenyfin.teenymoney.domain.permission.dto.response.PermissionResponseChildDTO;
import com.teenyfin.teenymoney.domain.permission.dto.response.PermissionResponseDTO;
import com.teenyfin.teenymoney.domain.permission.dto.response.PermissionResponseWrapperDTO;
import com.teenyfin.teenymoney.domain.permission.mapper.PermissionMapper;
import com.teenyfin.teenymoney.domain.permission.vo.PermissionVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.storage.S3Storage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionMapper permissionMapper;
    private final MemberMapper memberMapper;
    private final S3Storage s3Storage;

    // 오늘 날짜에 생성된 오늘만 허용 요청 조회
    @Transactional(readOnly = true)
    public PermissionResponseWrapperDTO getPermission(Long memberId, String role) {

        List<PermissionVO> permissionVOList = switch (role) {
            case "PARENT" -> permissionMapper.selectCreatedTodayByParentId(memberId);
            case "CHILD" -> permissionMapper.selectCreatedTodayByChildId(memberId);
            default -> throw new BusinessException(CategoryPolicyErrorCode.INVALID_ROLE); // 추후 MemberErrorCode 추가 시 변경
        };

        // 오늘만 허용 요청이 없는 경우
        if (permissionVOList.isEmpty()) {
            return PermissionResponseWrapperDTO.builder()
                    .isExist(false)
                    .permission(null)
                    .build();
        }

        // 자녀 정보
        MemberVO childVO = memberMapper.selectById(permissionVOList.get(0).getChildId());

        // 카테고리 외의 정보는 첫 번째 row에서 그대로 가져옴
        PermissionVO permissionVO = permissionVOList.get(0);

        // 카테고리 이름 리스트로 변환
        List<String> categoryNameList = permissionVOList.stream()
                .map(PermissionVO::getCategoryName)
                .toList();

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
}
