package com.teenyfin.teenymoney.domain.permission.mapper;

import com.teenyfin.teenymoney.domain.permission.vo.PermissionInsertVO;
import com.teenyfin.teenymoney.domain.permission.vo.PermissionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PermissionMapper {

    // 오늘만 허용 요청 조회
    PermissionVO selectById(@Param("permissionId") Long permissionId);

    // 부모 아이디로 오늘 생성된 오늘만 허용 요청 조회
    PermissionVO selectCreatedTodayByParentId(@Param("parentId") Long parentId);

    // 자녀 아이디로 오늘 생성된 오늘만 허용 요청 조회
    PermissionVO selectCreatedTodayByChildId(@Param("childId") Long childId);

    // 오늘만 허용 대상 카테고리 조회
    List<String> selectPermissionCategoriesByPermissionId(@Param("permissionId") Long permissionId);

    // 오늘만 허용 요청 생성
    void insertPermission(PermissionInsertVO permission);

    // 오늘만 허용 대상 카테고리 생성
    void insertPermissionCategories(@Param("permissionId") Long permissionId, @Param("categoryIds") List<Long> categoryIds);

    // 오늘만 허용 요청의 사유 수정
    void updatePermissionReason(@Param("permissionId") Long permissionId, @Param("reason") String reason);

    // 오늘만 허용 요청의 상태 수정
    void updatePermissionStatus(@Param("permissionId") Long permissionId, @Param("status") String status);

    // 오늘만 허용 요청 삭제
    void deletePermissionById(@Param("permissionId") Long permissionId);

    // 오늘만 허용 대상 카테고리 삭제
    void deletePermissionCategoriesByPermissionId(@Param("permissionId") Long permissionId);

    // (임시) 자녀 아이디로 연결된 부모 아이디 조회
    Long selectParentIdByChildId(@Param("childId") Long childId);
}
