package com.teenyfin.teenymoney.domain.notification.mapper;

import com.teenyfin.teenymoney.domain.notification.vo.NotificationVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface NotificationMapper {

    // 새로운 알림 내역 삽입
    void insert(NotificationVO notificationVO);

    // 최근 30일 간의 알림 내역을 최신순 커서 기반으로 조회 (cursorTime/cursorId가 null이면 첫 페이지)
    List<NotificationVO> selectRecentNotifications(
            @Param("memberId") Long memberId,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);

    // 단일 알림 읽음 처리
    void updateIsReadTrue(@Param("notificationId") Long notificationId);

    // 전체 알림 읽음 처리
    void updateAllIsReadTrue(@Param("memberId") Long memberId);

    // 읽지 않은 알림 개수 조회
    int countIsReadFalse(@Param("memberId") Long memberId);
}
