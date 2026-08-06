package com.teenyfin.teenymoney.domain.permission.mapper;

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
}
