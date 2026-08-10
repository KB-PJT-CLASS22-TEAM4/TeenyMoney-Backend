package com.teenyfin.teenymoney.domain.categoryPolicy.mapper;

import com.teenyfin.teenymoney.domain.categoryPolicy.dto.request.CategoryPolicyUpdateRequestDTO;
import com.teenyfin.teenymoney.domain.categoryPolicy.vo.CategoryPolicyVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CategoryPolicyMapper {

    // 아이디로 카테고리 정책 조회
    CategoryPolicyVO selectById(@Param("id") Long id);

    // 자녀 아이디로 전체 카테고리 정책 조회
    List<CategoryPolicyVO> selectByChildId(@Param("childId") Long childId);

    // 전체 카테고리 일괄 수정
    int updateAllPolicies(@Param("parentId") Long parentId, @Param("childId") Long childId, @Param("categoryPolicyList") List<CategoryPolicyUpdateRequestDTO> categoryPolicyList);

    // 전체 카테고리에 대해 기본 정책으로 초기 설정
    int insertDefaultPolicies(@Param("parentId") Long parentId, @Param("childId") Long childId);

    // 업종 코드로 업종 카테고리 아이디 조회
    Long selectCategoryIdByMerchantCode(@Param("merchantCode") String merchantCode);

    // 자녀 아이디와 업종 코드로 특정 업종 카테고리 정책 조회
    CategoryPolicyVO selectByMerchantCodeAndChildId(@Param("merchantCode") String merchantCode, @Param("childId") Long childId);
}


