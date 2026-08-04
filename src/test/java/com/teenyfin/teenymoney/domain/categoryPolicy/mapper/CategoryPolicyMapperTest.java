package com.teenyfin.teenymoney.domain.categoryPolicy.mapper;

import com.teenyfin.teenymoney.config.RootConfig;
import com.teenyfin.teenymoney.domain.categoryPolicy.vo.CategoryPolicyVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void 연동_성공시_모든_카테고리에_대한_기본_정책이_생성된다() {
        // given: 아직 정책이 없는 신규 자녀
        Long parentId = 1L;
        Long childId = 3L;

        // when
        categoryPolicyMapper.insertDefaultPolicies(parentId, childId);
        List<CategoryPolicyVO> result = categoryPolicyMapper.selectByChildId(childId);

        // then
        assertThat(result).isNotEmpty();
        assertThat(result).allSatisfy(policy -> {
            assertThat(policy.getPolicy()).isNotNull();
        });
    }

    @Test
    void 이미_정책이_존재하는_자녀에게_다시_초기화하면_UNIQUE_제약으로_실패한다() {
        // given: 시드 데이터상 이미 정책이 존재하는 자녀
        Long parentId = 1L;
        Long childId = 2L;

        // when & then
        assertThatThrownBy(() ->
                categoryPolicyMapper.insertDefaultPolicies(parentId, childId)
        ).isInstanceOf(DuplicateKeyException.class);
    }
}