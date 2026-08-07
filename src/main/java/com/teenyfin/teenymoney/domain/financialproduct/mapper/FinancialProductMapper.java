package com.teenyfin.teenymoney.domain.financialproduct.mapper;

import com.teenyfin.teenymoney.domain.financialproduct.vo.DepositProductVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductBenefitVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.LoanProductVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.SavingProductVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FinancialProductMapper {
    List<DepositProductVO> selectActiveDepositProducts();
    List<SavingProductVO> selectActiveSavingProducts();
    List<LoanProductVO> selectActiveLoanProducts();
    DepositProductVO selectActiveDepositProductById(@Param("id") Long id);
    SavingProductVO selectActiveSavingProductById(@Param("id") Long id);
    LoanProductVO selectActiveLoanProductById(@Param("id") Long id);
    FinancialProductBenefitVO selectBenefitByChildId(
            @Param("childId") Long childId);
    int upsertDepositProduct(DepositProductVO product);
    int upsertSavingProduct(SavingProductVO product);
}
