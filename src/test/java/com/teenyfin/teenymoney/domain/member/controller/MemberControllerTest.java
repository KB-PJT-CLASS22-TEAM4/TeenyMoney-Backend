package com.teenyfin.teenymoney.domain.member.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.teenyfin.teenymoney.domain.member.dto.response.MemberMeResponseDTO;
import com.teenyfin.teenymoney.domain.member.service.MemberService;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class MemberControllerTest {

    private MemberService memberService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        memberService = mock(MemberService.class);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(new MemberController(memberService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
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
    void getMeUsesAuthenticatedMemberIdAndReturnsMemberInformation() throws Exception {
        when(memberService.getMe(17L)).thenReturn(memberResponse());

        var response = mockMvc.perform(get("/members/me"))
                .andReturn().getResponse();

        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertEquals(200, response.getStatus(), body);
        assertTrue(body.contains("\"memberId\":17"), body);
        assertTrue(body.contains("\"role\":\"PARENT\""), body);
        assertTrue(body.contains("\"name\":\"Test User\""), body);
        assertTrue(body.contains("\"email\":\"user@example.com\""), body);
        assertTrue(body.contains("\"phoneNumber\":\"01012345678\""), body);
        assertTrue(body.contains("\"birthDate\":\"1990-01-02\""), body);
        assertTrue(body.contains(
                "\"profileImageUrl\":\"https://example.com/profile.png\""), body);
        verify(memberService).getMe(17L);
    }

    private MemberMeResponseDTO memberResponse() {
        MemberVO member = new MemberVO();
        member.setId(17L);
        member.setRole("PARENT");
        member.setName("Test User");
        member.setEmail("user@example.com");
        member.setPhoneNumber("01012345678");
        member.setBirthDate(LocalDate.of(1990, 1, 2));
        member.setProfileImageUrl("https://example.com/profile.png");
        return MemberMeResponseDTO.of(member);
    }
}
