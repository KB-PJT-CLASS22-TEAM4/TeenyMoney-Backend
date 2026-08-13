package com.teenyfin.teenymoney.domain.notification.mapper;

import com.teenyfin.teenymoney.domain.notification.vo.MemberNotificationVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemberNotificationMapper {

    // 멤버 테이블에서 알림 관련 정보만 조회
    MemberNotificationVO selectNotificationInfo(@Param("memberId") Long memberId);
}
