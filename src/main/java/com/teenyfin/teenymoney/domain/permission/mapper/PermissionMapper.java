package com.teenyfin.teenymoney.domain.permission.mapper;

import com.teenyfin.teenymoney.domain.permission.vo.PermissionInsertVO;
import com.teenyfin.teenymoney.domain.permission.vo.PermissionStatus;
import com.teenyfin.teenymoney.domain.permission.vo.PermissionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PermissionMapper {

    // 오늘만 허용 요청 조회
    PermissionVO selectById(@Param("permissionId") Long permissionId);

    // 자녀 아이디로 오늘 생성된 오늘만 허용 요청 조회
    List<PermissionVO> selectCreatedTodayByChildId(@Param("childId") Long childId);

    // 이번 달에 오늘만 허용을 신청한 일수 계산
    int countCreatedAtThisMonth(@Param("childId") Long childId);

    // 오늘만 허용 요청 생성
    void insertPermission(PermissionInsertVO permission);

    // 오늘만 허용 요청의 사유 수정
    void updatePermissionReason(@Param("permissionId") Long permissionId, @Param("reason") String reason);

    // 오늘만 허용 요청의 상태 수정
    void updatePermissionStatus(@Param("permissionId") Long permissionId, @Param("status") PermissionStatus status);

    // 오늘만 허용 요청 삭제
    void deletePermissionById(@Param("permissionId") Long permissionId);

}
