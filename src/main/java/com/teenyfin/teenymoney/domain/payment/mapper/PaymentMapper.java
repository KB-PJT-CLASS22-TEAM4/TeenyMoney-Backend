package com.teenyfin.teenymoney.domain.payment.mapper;

import com.teenyfin.teenymoney.domain.payment.vo.PaymentVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentMapper {

    // 해당 카테고리에서 최근 30일 간 결제한 횟수 조회
    int countRecentTransactions(@Param("childId") Long childId, @Param("categoryId") Long categoryId);

    // 해당 카테고리에 최근 30일 간 결제한 금액 조회
    Long sumRecentTransactionAmount(@Param("childId") Long childId, @Param("categoryId") Long categoryId);

    // 결제 내역 삽입
    int insert(PaymentVO paymentVO);

    // 멱등성 키로 결제 내역 조회
    PaymentVO selectByIdempotencyKey(@Param("childId") String idempotencyKey);

}
