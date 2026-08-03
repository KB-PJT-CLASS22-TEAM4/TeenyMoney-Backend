package com.teenyfin.teenymoney.domain.wallet.mapper;

import com.teenyfin.teenymoney.domain.wallet.vo.WalletTransactionVO;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WalletMapper {

    WalletVO selectMemberWalletByMemberId(@Param("memberId") Long memberId);

    List<WalletTransactionVO> selectRecentTransactions(
            @Param("walletId") Long walletId,
            @Param("limit") int limit);
}
