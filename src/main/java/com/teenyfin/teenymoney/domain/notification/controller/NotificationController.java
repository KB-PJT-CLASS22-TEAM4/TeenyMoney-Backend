package com.teenyfin.teenymoney.domain.notification.controller;

import com.teenyfin.teenymoney.domain.notification.service.NotificationService;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
