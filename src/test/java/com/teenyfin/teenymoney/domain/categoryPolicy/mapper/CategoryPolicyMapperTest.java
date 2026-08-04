package com.teenyfin.teenymoney.domain.categoryPolicy.mapper;

import com.teenyfin.teenymoney.config.RootConfig;
import com.teenyfin.teenymoney.domain.categoryPolicy.vo.CategoryPolicyVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RootConfig.class)
@Transactional
class CategoryPolicyMapperTest {

    @Autowired
    private CategoryPolicyMapper categoryPolicyMapper;

    @Test
    void 부모_아이디로_정책이_조회된다() {
        // given: DB에 미리 넣어둔 시드 데이터 사용
        Long parentId = 1L;

        // when
        List<CategoryPolicyVO> result = categoryPolicyMapper.selectByParentId(parentId);

        // then
        assertThat(result).isNotEmpty();
        assertThat(result.get(0).getMerchantCategoryName()).isNotNull();
        assertThat(result.get(0).getPolicy()).isNotNull();
    }

    @Test
    void 자녀_아이디로_정책이_조회된다() {
        // given
        Long childId = 2L;

        // when
        List<CategoryPolicyVO> result = categoryPolicyMapper.selectByChildId(childId);

        // then
        assertThat(result).isNotEmpty();
    }

    @Test
    void 데이터_없는_아이디는_빈_리스트가_반환된다() {
        // given
        Long parentId = 99999L;

        // when
        List<CategoryPolicyVO> result = categoryPolicyMapper.selectByParentId(parentId);

        // then
        assertThat(result).isEmpty();
    }
}