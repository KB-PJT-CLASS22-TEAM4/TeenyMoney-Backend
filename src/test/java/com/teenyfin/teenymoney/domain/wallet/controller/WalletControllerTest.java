package com.teenyfin.teenymoney.domain.wallet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.teenyfin.teenymoney.domain.wallet.dto.response.WalletDetailResponseDTO;
import com.teenyfin.teenymoney.domain.wallet.service.WalletService;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletTransactionVO;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MockMvcBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WalletControllerTest {


    private WalletService walletService;
    private MockMvc mockMvc;


    @BeforeEach
    void setUp() {
        // 진짜 WalletService 대신 가짜(mock)를 넣어서, Service 내부 로직 말고
        // "Controller가 로그인한 사람의 memberId로 Service를 잘 부르고, 응답을 잘 감싸는지"만 본다
        walletService = mock(WalletService.class);

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        mockMvc = MockMvcBuilders.standaloneSetup(new WalletController(walletService)).setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper)).build();
        // @AuthenticationPrincipal 파라미터를 실제 서버 없이도 채워주기 위한 설정

        // "로그인한 사람은 memberId=17, role=PARENT다"라고 미리 SecurityContext에 심어둠
        // (실제 서비스에서는 JwtAuthenticationFilter가 이 일을 대신 해줌)
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new MemberPrincipal(17L, "PARENT"), null,
                List.of()));
    }


    @AfterEach
    void tearDown() {
        //다음 테스트에 영향 안 주게 매번 SecurityContext 비우기
        SecurityContextHolder.clearContext();
    }
    @Test
    void getMyWalletDetailUsesAuthenticatedMemberIdAndReturnsBalanceAndTransactions() throws Exception {
        // walletService.getMyWalletDetail(17L)가 호출되면 이 값을 반환하라고 미리 정해둠
        when(walletService.getMyWalletDetail(17L)).thenReturn(walletDetailResponse());

        var response = mockMvc.perform(get("/wallet/me"))
                .andReturn().getResponse();

        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertEquals(200, response.getStatus(), body);
        assertTrue(body.contains("\"walletId\":5"), body);
        assertTrue(body.contains("\"balance\":150000"), body);
        assertTrue(body.contains("\"direction\":\"DEBIT\""), body);
        assertTrue(body.contains("\"description\":\"편의점 결제\""), body);

        // Controller가 SecurityContext에 심어둔 17L로 정확히 Service를 호출했는지까지 확인
        verify(walletService).getMyWalletDetail(17L);
    }

    // --- 테스트용 가짜 응답 데이터 조립 ---

    private WalletDetailResponseDTO walletDetailResponse() {
        WalletVO wallet = new WalletVO();
        wallet.setId(5L);
        wallet.setMemberId(17L);
        wallet.setBalance(150000L);
        wallet.setType("MEMBER");
        wallet.setUpdatedAt(LocalDateTime.of(2026, 7, 31, 16, 15, 43));

        WalletTransactionVO transaction = new WalletTransactionVO();
        transaction.setId(1L);
        transaction.setDirection("DEBIT");
        transaction.setAmount(3500L);
        transaction.setBalanceAfter(146500L);
        transaction.setDescription("편의점 결제");
        transaction.setCreatedAt(LocalDateTime.of(2026, 7, 31, 16, 15, 43));

        return WalletDetailResponseDTO.of(wallet, List.of(transaction));
    }
}