package com.teenyfin.teenymoney.global.sse;

import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.exception.CommonErrorCode;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponses;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpServletResponse;

/**
 * 실시간 화면 동기화 엔드포인트.
 *
 * 경로를 "/sse"로 적는다. ServletConfig.configurePathMatch가 @RestController에 /api/v1을
 * 붙이므로 실제 경로는 /api/v1/sse/... 가 된다. 여기에 /api/v1을 적으면 두 번 붙는다.
 *
 * 두 엔드포인트의 인증 방식이 다르다.
 *   POST /ticket    : 평소대로 Authorization: Bearer 로 인증한다
 *   GET  /subscribe : SecurityConfig 화이트리스트에 있고, 티켓으로 직접 신원을 확인한다
 *
 * subscribe를 화이트리스트에 넣는 이유는 JwtAuthenticationFilter가 막아서가 아니다.
 * 그 필터는 헤더가 없으면 아무 것도 하지 않고 그냥 통과시킨다. 걸리는 것은
 * SecurityConfig의 anyRequest().authenticated() 규칙이다.
 *
 * 화이트리스트 경로라 RestAuthenticationEntryPoint가 타지 않으므로, 티켓 실패는 컨트롤러가
 * BusinessException으로 던지고 GlobalExceptionAdvice가 401 응답을 만든다.
 */
@Api(tags = "SSE", description = "실시간 화면 동기화 (Server-Sent Events)")
@RestController
@RequestMapping("/sse")
public class SseController {

    private final SseTicketStore ticketStore;
    private final SseEmitterRegistry registry;

    public SseController(SseTicketStore ticketStore, SseEmitterRegistry registry) {
        this.ticketStore = ticketStore;
        this.registry = registry;
    }

    @ApiOperation(
            value = "SSE 구독 티켓 발급",
            notes = "구독에 쓸 1회용 티켓을 발급한다. 응답은 {\"ticket\": \"<uuid>\"} 이다.\n\n"
                    + "유효기간 30초, 사용 횟수 1회다. 발급 즉시 GET /api/v1/sse/subscribe 를 호출한다.\n"
                    + "액세스 토큰을 쿼리 파라미터에 넣지 않기 위한 우회이므로, 이 API 자체는 "
                    + "평소대로 Authorization: Bearer 헤더로 인증한다.")
    @PostMapping("/ticket")
    public ApiResponse<SseTicketResponseDTO> issueTicket(
            @AuthenticationPrincipal MemberPrincipal principal) {
        return ApiResponse.ok(new SseTicketResponseDTO(ticketStore.issue(principal.memberId())));
    }

    @ApiOperation(
            value = "실시간 화면 동기화 구독",
            notes = "text/event-stream 을 연다. springfox가 이 형식을 표현하지 못하므로 아래에 글로 적는다.\n\n"
                    + "■ 사용법\n"
                    + "  1) POST /api/v1/sse/ticket 으로 티켓을 받는다\n"
                    + "  2) new EventSource('/api/v1/sse/subscribe?ticket=' + ticket)\n"
                    + "  3) 티켓은 1회용이라 EventSource의 자동 재연결은 반드시 실패한다. "
                    + "onerror에서 close() 후 티켓을 새로 받아 새 EventSource를 만들 것\n\n"
                    + "■ 내려오는 것\n"
                    + "  event 이름은 NotificationReferenceType 값 그대로다 - "
                    + "QUEST, TODAY_PERMISSION, CONNECTION, TRANSFER, PAYMENT, CHARGE, "
                    + "DEPOSIT_ENROLLMENT, SAVING_ENROLLMENT, LOAN_ENROLLMENT.\n"
                    + "  data 는 항상 {} 다. 데이터를 싣지 않는다 - 신호를 받으면 해당 화면의 "
                    + "기존 조회 API를 다시 호출한다.\n"
                    + "  그밖에 주기적으로 ':ping' 주석 라인이 온다(연결 유지용, 이벤트 아님).\n\n"
                    + "■ 왜 FCM이 아니라 이것인가\n"
                    + "  알림 설정을 꺼도 화면 동기화는 살아 있어야 하기 때문이다. "
                    + "FCM은 앱 밖에서 사용자를 부르는 일만 하고, 이 채널은 앱 안에서 화면을 최신으로 유지한다.")
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "스트림이 열렸다"),
            @io.swagger.annotations.ApiResponse(
                    code = 401, message = "티켓이 없거나 이미 소비됐거나 만료됐다")
    })
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(
            @RequestParam String ticket,
            HttpServletResponse response) {

        Long memberId = ticketStore.consume(ticket);
        if (memberId == null) {
            throw new BusinessException(CommonErrorCode.AUTH_UNAUTHORIZED);
        }

        // 앞단 리버스 프록시가 응답을 버퍼링하면 이벤트도 하트비트도 한 글자도 내려가지
        // 않는데 연결은 열려 있는 것처럼 보인다. nginx는 proxy_buffering이 기본 on이다.
        // 이 헤더는 인프라 설정을 못 건드리는 상태에서 nginx에게 끄라고 말하는 유일한 수단이다.
        response.setHeader("X-Accel-Buffering", "no");

        return registry.add(memberId);
    }
}
