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



    // 지갑을 "잠근 채로" 조회한다 (SQL: SELECT ... FOR UPDATE).
    // 이 메서드로 조회한 순간부터, 이 지갑 행은 지금 트랜잭션이 끝날 때까지
    // 다른 트랜잭션이 동시에 못 건드리게 잠긴다 (동시에 잔액 깎다가 꼬이는 걸 방지).
    WalletVO selectWalletForUpdate(@Param("walletId") Long walletId);

    // 위에서 잠근 지갑의 잔액을 새 값으로 덮어쓴다.
    // "새 잔액"은 Java 코드(WalletLedgerService)에서 (현재잔액 ± amount)로 미리 계산해서 넘겨준다.
    void updateBalance(@Param("walletId") Long walletId, @Param("balance") Long balance);

    // T_WLT_HIST_H(원장)에 한 줄을 새로 남긴다.
    // refType/refId는 여기서 실제로 payment_id/transfer_id/charge_id 중 어느 컬럼에
    // refId 값을 넣을지를 결정하는 데 쓰인다 (XML의 <choose>가 이 분기를 담당).
    void insertWalletHistory(
            @Param("walletId") Long walletId,
            @Param("direction") String direction,
            @Param("amount") Long amount,
            @Param("balanceAfter") Long balanceAfter,
            @Param("refType") String refType,
            @Param("refId") Long refId,
            @Param("description") String description);
}
