package com.teenyfin.teenymoney.domain.family.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.teenyfin.teenymoney.domain.family.dto.response.FamilyLinkCodeResponseDTO;
import com.teenyfin.teenymoney.domain.family.service.FamilyLinkCodeService;
import com.teenyfin.teenymoney.global.exception.GlobalExceptionAdvice;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Idempotency-Key가 HTTP 레벨에서 실제로 강제되는지 본다.
 *
 * 서비스 단위 테스트는 '서비스에 null이 들어오면 거절한다'까지만 증명한다.
 * 헤더가 아예 없을 때 스프링이 던지는 MissingRequestHeaderException이 400으로 나가는지는
 * 여기서만 드러난다. 전용 핸들러가 없으면 catch-all에 걸려 500이 나가고,
 * 프론트는 자기 실수를 서버 장애로 오인한다.
 */
class FamilyControllerTest {

    private static final String PATH = "/families/make-codes";

    private FamilyLinkCodeService familyLinkCodeService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        familyLinkCodeService = mock(FamilyLinkCodeService.class);

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        mockMvc = MockMvcBuilders
                .standaloneSetup(new FamilyController(familyLinkCodeService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setControllerAdvice(new GlobalExceptionAdvice())
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new MemberPrincipal(17L, "PARENT"), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Idempotency-Key 헤더가 없으면 400 COMMON_MISSING_HEADER를 받는다")
    void missingIdempotencyKeyHeaderReturnsBadRequest() throws Exception {
        var response = mockMvc.perform(post(PATH)).andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);

        assertEquals(400, response.getStatus(), body);
        assertTrue(body.contains("\"success\":false"), body);
        assertTrue(body.contains("\"code\":\"COMMON_MISSING_HEADER\""), body);
        assertTrue(body.contains("Idempotency-Key"), body);

        // 헤더가 없으면 서비스까지 가지 않는다.
        verify(familyLinkCodeService, never()).makeCode(any(), anyString());
    }

    @Test
    @DisplayName("Idempotency-Key 헤더는 서비스로 그대로 전달된다")
    void passesIdempotencyKeyToService() throws Exception {
        when(familyLinkCodeService.makeCode(eq(17L), eq("intent-1")))
                .thenReturn(new FamilyLinkCodeResponseDTO(
                        "048291", OffsetDateTime.parse("2026-08-06T14:35:12+09:00")));

        var response = mockMvc.perform(post(PATH).header("Idempotency-Key", "intent-1"))
                .andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);

        assertEquals(200, response.getStatus(), body);
        assertTrue(body.contains("\"code\":\"048291\""), body);

        verify(familyLinkCodeService).makeCode(17L, "intent-1");
    }

    @Test
    @DisplayName("자녀 연동 요청의 6자리 코드를 서비스에 전달한다")
    void passesLinkCodeToService() throws Exception {
        var response = mockMvc.perform(post("/families/connect-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"048291\"}"))
                .andReturn().getResponse();

        assertEquals(200, response.getStatus());
        verify(familyLinkCodeService).linkChild(17L, "048291");
    }

    @Test
    @DisplayName("6자리 숫자가 아닌 연동 코드는 400으로 거절한다")
    void rejectsInvalidLinkCodeFormat() throws Exception {
        var response = mockMvc.perform(post("/families/connect-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"12AB\"}"))
                .andReturn().getResponse();

        assertEquals(400, response.getStatus());
        verify(familyLinkCodeService, never()).linkChild(any(), anyString());
    }
}
