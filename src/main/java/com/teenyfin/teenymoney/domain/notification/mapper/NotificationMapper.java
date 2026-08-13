package com.teenyfin.teenymoney.domain.notification.mapper;

import com.teenyfin.teenymoney.domain.notification.vo.NotificationVO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationMapper {

    // 새로운 알림 내역 삽입
    void insert(NotificationVO notificationVO);
}
