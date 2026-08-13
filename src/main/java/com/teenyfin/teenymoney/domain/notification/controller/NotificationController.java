package com.teenyfin.teenymoney.domain.notification.controller;

import com.teenyfin.teenymoney.domain.notification.dto.response.NotificationResponseDTO;
import com.teenyfin.teenymoney.domain.notification.service.NotificationService;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "Notification", description = "알림 API")
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @ApiOperation(value = "푸시 알림 테스트", notes = "푸시 알림 기능을 테스트하기 위한 API입니다.")
    @GetMapping("/test")
    public ApiResponse<Void> testNotification(
            @AuthenticationPrincipal MemberPrincipal memberPrincipal) {
        notificationService.createNotification(memberPrincipal.memberId(), "결제가 완료됐어요", "GS25 강남점 · 3,200원", NotificationReferenceType.PAYMENT, 1L, true);
        return ApiResponse.ok();
    }

    @ApiOperation(value = "내 알림 최신순 조회", notes = "최근 30일 간의 알림 내역을 조회합니다. 최신순으로 정렬하여 10건씩 조회합니다.")
    @GetMapping
    public ApiResponse<List<NotificationResponseDTO>> getNotifications(
            @AuthenticationPrincipal MemberPrincipal memberPrincipal) {
        return ApiResponse.ok(notificationService.getNotifications(memberPrincipal.memberId()));
    }

    @ApiOperation(value = "단일 알림 읽음 처리", notes = "하나의 알림을 읽음 처리합니다.")
    @PatchMapping("/{notificationId}")
    public ApiResponse<Void> readNotification(
            @AuthenticationPrincipal MemberPrincipal memberPrincipal,
            @PathVariable Long notificationId) {
        notificationService.readNotification(memberPrincipal.memberId(), notificationId);
        return ApiResponse.ok();
    }

    @ApiOperation(value = "전체 알림 읽음 처리", notes = "모든 알림을 읽음 처리합니다.")
    @PatchMapping
    public ApiResponse<Void> readAllNotifications(
            @AuthenticationPrincipal MemberPrincipal memberPrincipal) {
        notificationService.readAllNotifications(memberPrincipal.memberId());
        return ApiResponse.ok();
    }
}
