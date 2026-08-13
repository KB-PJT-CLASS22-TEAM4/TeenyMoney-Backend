package com.teenyfin.teenymoney.domain.notification.mapper;

import com.teenyfin.teenymoney.domain.notification.vo.NotificationVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NotificationMapper {

    // 새로운 알림 내역 삽입
    void insert(NotificationVO notificationVO);

    // 최근 30일 간의 알림 내역 조회
    List<NotificationVO> selectRecentNotifications(@Param("memberId") Long memberId);

    // 단일 알림 읽음 처리
    void updateIsReadTrue(@Param("notificationId") Long notificationId);

    // 전체 알림 읽음 처리
    void updateAllIsReadTrue(@Param("memberId") Long memberId);
}
