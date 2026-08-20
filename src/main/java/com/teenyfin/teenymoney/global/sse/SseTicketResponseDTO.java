package com.teenyfin.teenymoney.global.sse;

/** 발급된 SSE 티켓. 30초 안에 GET /sse/subscribe?ticket= 로 한 번만 쓸 수 있다. */
public record SseTicketResponseDTO(String ticket) {
}
