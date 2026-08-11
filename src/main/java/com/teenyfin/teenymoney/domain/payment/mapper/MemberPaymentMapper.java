package com.teenyfin.teenymoney.domain.payment.mapper;

import com.teenyfin.teenymoney.domain.payment.vo.MemberPaymentVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface MemberPaymentMapper {

    // 멤버 아이디로 결제 관련 정보 조회
    MemberPaymentVO selectByMemberId(@Param("memberId") Long memberId);

    // 결제 비밀번호 실패 횟수 1 증가
    void incrementPaymentPasswordFailedCount(@Param("memberId") Long memberId);

    // 결제 비밀번호 실패 횟수 초기화
    void resetPaymentPasswordFailedCount(@Param("memberId") Long memberId);

    // 잠금 해제 시간 수정
    void updatePaymentLockedUntil(@Param("memberId") Long memberId, @Param("paymentLockedUntil") LocalDateTime paymentLockedUntil);
}
