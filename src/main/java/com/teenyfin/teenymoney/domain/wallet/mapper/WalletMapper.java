package com.teenyfin.teenymoney.domain.wallet.mapper;

import com.teenyfin.teenymoney.domain.wallet.vo.WalletTransactionVO;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface WalletMapper {

    WalletVO selectMemberWalletByMemberId(@Param("memberId") Long memberId);

    List<WalletTransactionVO> selectRecentTransactions(
            @Param("walletId") Long walletId,
            @Param("limit") int limit);

    // 거래내역 목록 조회 (종류/기간/정렬 필터 적용판).
    // direction은 "CREDIT"/"DEBIT" 문자열이거나, 전체(ALL) 선택 시 null이 넘어온다.
    // null이면 XML 쪽 <if>가 direction 조건 자체를 SQL에서 빼버린다.
    List<WalletTransactionVO> selectTransactions(
            @Param("walletId") Long walletId,
            @Param("direction") String direction,
            @Param("startDate") LocalDate startDate,
            @Param("sortOrder") String sortOrder);

}
