package com.teenyfin.teenymoney.domain.charge.mapper;

import com.teenyfin.teenymoney.domain.charge.vo.ChargeVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;



@Mapper
public interface ChargeMapper {

    ChargeVO selectByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    void insertCharge(ChargeVO charge);

    ChargeVO selectById(@Param("id") Long id);

    ChargeVO selectForUpdate(@Param("id") Long id);

    int claimForProcessing(@Param("id") Long id);

    void markSuccess(@Param("id") Long id, @Param("paymentKey") String paymentKey);

    void markFailed(@Param("id") Long id, @Param("failureReason") String failureReason);
}

