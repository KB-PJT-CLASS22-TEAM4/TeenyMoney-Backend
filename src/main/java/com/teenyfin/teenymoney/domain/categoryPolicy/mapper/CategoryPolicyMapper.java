package com.teenyfin.teenymoney.domain.categoryPolicy.mapper;

import com.teenyfin.teenymoney.domain.categoryPolicy.vo.CategoryPolicyVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CategoryPolicyMapper {

    List<CategoryPolicyVO> selectByParentId(@Param("parentId") Long parentId);
    List<CategoryPolicyVO> selectByChildId(@Param("childId") Long childId);
}


