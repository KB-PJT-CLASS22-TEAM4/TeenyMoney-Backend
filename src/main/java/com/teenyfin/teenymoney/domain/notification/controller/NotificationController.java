package com.teenyfin.teenymoney.domain.notification.controller;

import com.teenyfin.teenymoney.domain.notification.dto.request.NotificationFcmRequestDTO;
import com.teenyfin.teenymoney.domain.notification.dto.request.NotificationSettingRequestDTO;
import com.teenyfin.teenymoney.domain.notification.dto.response.NotificationListResponseDTO;
import com.teenyfin.teenymoney.domain.notification.dto.response.NotificationSettingResponseDTO;
import com.teenyfin.teenymoney.domain.notification.service.NotificationService;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@Api(tags = "Notification", description = "알림 API")
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @ApiOperation(value = "푸시 알림 테스트", notes = "푸시 알림 기능을 테스트하기 위한 API입니다.")
    @PostMapping("/test")
    public ApiResponse<Void> testNotification(
            @AuthenticationPrincipal MemberPrincipal memberPrincipal) {
        notificationService.createNotification(memberPrincipal.memberId(), "결제가 완료됐어요", "GS25 강남점 · 3,200원", NotificationReferenceType.PAYMENT, 1L, true);
        return ApiResponse.ok();
    }

    @ApiOperation(value = "내 알림 최신순 조회", notes = "최근 30일 간의 알림 내역을 최신순으로 10건씩 커서 기반 조회합니다. " +
            "cursor 없이 호출하면 첫 페이지를 반환하고, 응답의 nextCursor를 다음 요청의 cursor로 그대로 넘기면 다음 페이지를 받습니다. " +
            "nextCursor가 없으면 마지막 페이지입니다.")
    @GetMapping
    public ApiResponse<NotificationListResponseDTO> getNotifications(
            @AuthenticationPrincipal MemberPrincipal memberPrincipal,
            @RequestParam(required = false) String cursor) {
        return ApiResponse.ok(notificationService.getNotifications(memberPrincipal.memberId(), cursor));
    }

    @ApiOperation(value = "단일 알림 읽음 처리", notes = "하나의 알림을 읽음 처리합니다.")
    @PatchMapping("/{notificationId}")
    public ApiResponse<Void> readNotification(
            @AuthenticationPrincipal MemberPrincipal memberPrincipal,
            @PathVariable Long notificationId) {
        notificationService.readNotification(memberPrincipal.memberId(), notificationId);
        return ApiResponse.ok();
    }

    @ApiOperation(value = "전체 알림 읽음 처리", notes = "모든 알림을 읽음 처리합니다. 현재 페이지에서 가장 최근 알림의 아이디를 파라미터로 전달합니다.")
    @PatchMapping("/{notificationId}/all")
    public ApiResponse<Void> readAllNotifications(
            @AuthenticationPrincipal MemberPrincipal memberPrincipal,
            @PathVariable Long notificationId) {
        notificationService.readAllNotifications(memberPrincipal.memberId(), notificationId);
        return ApiResponse.ok();
    }

    @ApiOperation(value = "읽지 않은 알림 개수 조회", notes = "아직 읽지 않은 알림의 개수를 반환합니다.")
    @GetMapping("/unread")
    public ApiResponse<Integer> getUnreadNotificationCount(
            @AuthenticationPrincipal MemberPrincipal memberPrincipal) {
        return ApiResponse.ok(notificationService.getUnreadNotificationCount(memberPrincipal.memberId()));
    }

    @ApiOperation(value = "FCM 토큰 갱신", notes = "로그인 시 사용자의 FCM 토큰을 갱신합니다.")
    @PatchMapping("/fcm-token")
    public ApiResponse<Void> modifyFcmToken(
            @AuthenticationPrincipal MemberPrincipal memberPrincipal,
            @RequestBody @Valid NotificationFcmRequestDTO notificationFcmRequestDTO) {
        notificationService.modifyFcmToken(memberPrincipal.memberId(), notificationFcmRequestDTO);
        return ApiResponse.ok();
    }

    @ApiOperation(value = "알림 수신 여부 조회", notes = "현재 알림 수신 여부 관련 설정을 조회합니다.")
    @GetMapping("/setting")
    public ApiResponse<NotificationSettingResponseDTO> getNotificationSetting(
            @AuthenticationPrincipal MemberPrincipal memberPrincipal) {
        return ApiResponse.ok(notificationService.getNotificationSetting(memberPrincipal.memberId()));
    }

    @ApiOperation(value = "알림 수신 여부 변경", notes = "알림 수신 여부 관련 설정을 변경합니다.")
    @PatchMapping("/setting")
    public ApiResponse<Void> modifyNotificationSetting(
            @AuthenticationPrincipal MemberPrincipal memberPrincipal,
            @RequestBody @Valid NotificationSettingRequestDTO notificationSettingRequestDTO) {
        notificationService.modifyNotificationSetting(memberPrincipal.memberId(), notificationSettingRequestDTO);
        return ApiResponse.ok();
    }
}
