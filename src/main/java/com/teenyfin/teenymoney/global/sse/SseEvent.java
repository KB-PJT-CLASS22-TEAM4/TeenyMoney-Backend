package com.teenyfin.teenymoney.global.sse;

import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;

/**
 * "이 회원이 보고 있는 화면이 낡았다"는 신호.
 *
 * 데이터를 싣지 않는다. 클라이언트는 이 신호를 받으면 기존 조회 API를 다시 호출한다.
 * 그래서 SSE 전용 응답 스키마가 하나도 없고, 정합성은 조회 API 한 곳에서만 결정되며,
 * 인가 검사도 기존 API 것을 그대로 탄다.
 *
 * 이벤트 이름으로 NotificationReferenceType을 그대로 쓴다. 새 카탈로그를 만들지 않는
 * 이유는 이게 이미 그 카탈로그이기 때문이다 - 상태가 바뀌는 지점은 전부
 * NotificationService.createNotification()을 지나고, 거기서 이 타입을 이미 받고 있다.
 * 매핑 코드가 0줄이다.
 *
 * 발행은 ApplicationEventPublisher로 하고 수신은 SseEmitterRegistry가 한다.
 * 그 사이에 트랜잭션 커밋이 끼는 이유는 SseEmitterRegistry.onStateChanged() 참고.
 */
public record SseEvent(Long memberId, NotificationReferenceType type) {
}
