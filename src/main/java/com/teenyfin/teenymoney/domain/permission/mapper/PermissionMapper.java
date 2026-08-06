package com.teenyfin.teenymoney.domain.permission.mapper;

import com.teenyfin.teenymoney.domain.permission.vo.PermissionInsertVO;
import com.teenyfin.teenymoney.domain.permission.vo.PermissionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PermissionMapper {

    // 부모 아이디로 오늘 생성된 오늘만 허용 요청 조회
    List<PermissionVO> selectCreatedTodayByParentId(@Param("parentId") Long parentId);

    // 자녀 아이디로 오늘 생성된 오늘만 허용 요청 조회
    List<PermissionVO> selectCreatedTodayByChildId(@Param("childId") Long childId);

    // 오늘만 허용 요청 생성
    void insertPermissionRequest(PermissionInsertVO permission);

    // 오늘만 허용 대상 카테고리 생성
    void insertPermissionRequestCategories(
            @Param("permissionId") Long permissionId,
            @Param("categoryIds") List<Long> categoryIds
    );

    // (임시) 자녀 아이디로 연결된 부모 아이디 조회
    Long selectParentIdByChildId(@Param("childId") Long childId);
}
