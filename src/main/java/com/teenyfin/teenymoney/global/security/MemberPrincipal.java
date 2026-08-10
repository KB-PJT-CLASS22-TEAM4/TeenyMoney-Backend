package com.teenyfin.teenymoney.global.security;

/**
 * 인증 주체. Member 엔티티가 아니라 토큰 클레임에서 만든 값 객체다.
 *
 * JwtAuthenticationFilter가 만들어 SecurityContext에 담고,
 * 컨트롤러가 @AuthenticationPrincipal MemberPrincipal 로 주입받는다.
 *
 * DB를 조회하지 않는다. Member 엔티티를 담으면 매 요청 조회가 생겨
 * JWT로 저장소 조회를 없앤 의미가 사라지고, memberId만 담으면 role을 잃어
 * 권한 판단에 또 조회가 필요해진다. 그래서 토큰에 실려온 두 값만 담는다.
 *
 * record인 이유:
 *   - 불변이라 요청 처리 중 role이 바뀌는 경로가 없다
 *   - toString이 MemberPrincipal[memberId=17, role=PARENT] 로 나와 테스트에서 바로 읽힌다
 *   - 접근자가 getMemberId()가 아니라 memberId() 다 (호출측이 이 이름을 쓴다)
 *
 * 회원이 DB에 실제로 존재하는지는 확인하지 않는다. 그 확인은 로그인 시점에
 * 이미 끝났다는 전제다.
 */
public record MemberPrincipal(Long memberId, String role) {
}
