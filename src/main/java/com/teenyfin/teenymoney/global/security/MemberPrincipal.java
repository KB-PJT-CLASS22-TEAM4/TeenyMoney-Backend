package com.teenyfin.teenymoney.global.security;

/**
 * 인증 주체. Member 엔티티가 아니라 토큰 클레임에서 만든 값 객체다.
 * 컨트롤러에서 @AuthenticationPrincipal MemberPrincipal 로 주입받는다.
 */
public record MemberPrincipal(Long memberId, String role) {
}
