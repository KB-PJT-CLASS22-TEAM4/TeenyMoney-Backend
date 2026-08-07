package com.teenyfin.teenymoney.domain.payment.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentMapper {

    int countRecentTransactions(@Param("childId") Long childId, @Param("categoryId") Long categoryId);
    Long sumRecentTransactionAmount(@Param("childId") Long childId, @Param("categoryId") Long categoryId);
}
