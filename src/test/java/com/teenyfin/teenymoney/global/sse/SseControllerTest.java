package com.teenyfin.teenymoney.global.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teenyfin.teenymoney.global.exception.GlobalExceptionAdvice;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 구독 엔드포인트는 SecurityConfig 화이트리스트에 있어서 스프링 시큐리티가 걸러주지 않는다.
 * 신원 확인 전체를 이 컨트롤러가 직접 한다는 뜻이고, 그래서 여기가 뚫리면 남의 화면 신호를
 * 받아볼 수 있다. 아래 테스트가 그 방어선이다.
 */
class SseControllerTest {

    private final SseTicketStore ticketStore = mock(SseTicketStore.class);
    private final SseEmitterRegistry registry = mock(SseEmitterRegistry.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SseController(ticketStore, registry))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setControllerAdvice(new GlobalExceptionAdvice())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new MemberPrincipal(1L, "CHILD"), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("티켓 발급은 인증된 회원의 id로 발급한다")
    void 티켓은_인증된_회원으로_발급된다() throws Exception {
        given(ticketStore.issue(1L)).willReturn("ticket-uuid");

        MockHttpServletResponse response = mockMvc.perform(post("/sse/ticket"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("ticket-uuid");
    }

    @Test
    @DisplayName("없는 티켓으로 구독하면 401이고 연결을 열지 않는다")
    void 없는_티켓은_401이다() throws Exception {
        given(ticketStore.consume("garbage")).willReturn(null);

        MockHttpServletResponse response = mockMvc.perform(
                        get("/sse/subscribe").param("ticket", "garbage"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(401);
        verify(registry, never()).add(any());
    }

    @Test
    @DisplayName("이미 소비된 티켓을 다시 쓰면 401이다 - 티켓은 1회용이다")
    void 소비된_티켓_재사용은_401이다() throws Exception {
        // 첫 호출은 memberId를 돌려주고, 두 번째부터는 null이다(GETDEL이라 값이 사라진다).
        given(ticketStore.consume("ticket-uuid")).willReturn(7L, (Long) null);

        MockHttpServletResponse first = mockMvc.perform(
                        get("/sse/subscribe").param("ticket", "ticket-uuid"))
                .andReturn().getResponse();
        assertThat(first.getStatus()).isEqualTo(200);

        MockHttpServletResponse second = mockMvc.perform(
                        get("/sse/subscribe").param("ticket", "ticket-uuid"))
                .andReturn().getResponse();
        assertThat(second.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("구독이 열리면 티켓의 주인으로 연결을 등록하고 버퍼링 차단 헤더를 붙인다")
    void 구독은_티켓_주인으로_등록되고_버퍼링을_끈다() throws Exception {
        given(ticketStore.consume("ticket-uuid")).willReturn(7L);

        MockHttpServletResponse response = mockMvc.perform(
                        get("/sse/subscribe").param("ticket", "ticket-uuid"))
                .andReturn().getResponse();

        // 신원은 @AuthenticationPrincipal(=1L)이 아니라 티켓(=7L)에서 온다.
        verify(registry).add(7L);

        // 이 헤더가 없으면 nginx 뒤에서 아무것도 안 내려간다(proxy_buffering 기본 on).
        assertThat(response.getHeader("X-Accel-Buffering")).isEqualTo("no");
    }
}
