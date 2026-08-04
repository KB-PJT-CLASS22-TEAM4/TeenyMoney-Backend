package com.teenyfin.teenymoney.domain.categoryPolicy.mapper;

import com.teenyfin.teenymoney.domain.categoryPolicy.vo.CategoryPolicyVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CategoryPolicyMapper {

    List<CategoryPolicyVO> selectByParentId(@Param("parentId") Long parentId);
    List<CategoryPolicyVO> selectByChildId(@Param("childId") Long childId);

    // 전체 카테고리에 대해 디폴트 정책으로 초기 설정
    int insertDefaultPolicy(@Param("parentId") Long parentId, @Param("childId") Long childId);
}


