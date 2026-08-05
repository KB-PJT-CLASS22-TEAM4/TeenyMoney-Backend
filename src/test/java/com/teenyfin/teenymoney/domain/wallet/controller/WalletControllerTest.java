package com.teenyfin.teenymoney.domain.wallet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.teenyfin.teenymoney.domain.wallet.dto.request.TransactionPeriod;
import com.teenyfin.teenymoney.domain.wallet.dto.request.TransactionSortOrder;
import com.teenyfin.teenymoney.domain.wallet.dto.request.TransactionTypeFilter;
import com.teenyfin.teenymoney.domain.wallet.dto.response.WalletDetailResponseDTO;
import com.teenyfin.teenymoney.domain.wallet.dto.response.WalletTransactionResponseDTO;
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

    @Test
    void getMyTransactionsUsesDefaultFiltersWhenNoQueryParamsGiven() throws Exception {
        // 쿼리 파라미터를 아예 안 보냈을 때, Controller의 defaultValue(ALL/MONTH/DESC)가
        // 실제로 Service까지 그대로 전달되는지 확인한다.
        when(walletService.getMyTransactions(17L, TransactionTypeFilter.ALL, TransactionPeriod.MONTH, TransactionSortOrder.DESC))
                .thenReturn(List.of(sampleTransactionResponse()));

        // 쿼리스트링 없이 GET 요청 -> Controller의 @RequestParam(defaultValue=...) 값들이 적용돼야 함
        var response = mockMvc.perform(get("/wallet/me/transactions"))
                .andReturn().getResponse();

        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertEquals(200, response.getStatus(), body);
        // ApiResponse<List<...>> 로 형태로 나오니 JSON 문자열 안에 값이 들어있는지 문자열 포함 여부로 확인
        assertTrue(body.contains("\"direction\":\"DEBIT\""), body);
        assertTrue(body.contains("\"description\":\"편의점 결제\""), body);

        // 진짜 검증 포인트: 파라미터 없이 요청했는데도 Service가 (17L, ALL, MONTH, DESC)로 호출됐는지 확인
        // -> Controller의 @RequestParam(defaultValue="ALL"/"MONTH"/"DESC")가 실제로 동작한다는 증거
        verify(walletService).getMyTransactions(17L, TransactionTypeFilter.ALL, TransactionPeriod.MONTH, TransactionSortOrder.DESC);
    }

    @Test
    void getMyTransactionsPassesExplicitQueryParamsToServiceAsEnums() throws Exception {
        // 이번엔 쿼리 파라미터를 직접 실어 보낸다 -> Spring이 문자열("CREDIT")을
        // enum(TransactionTypeFilter.CREDIT)으로 알아서 변환해서 Service까지 넘기는지 확인
        when(walletService.getMyTransactions(17L, TransactionTypeFilter.CREDIT, TransactionPeriod.WEEK, TransactionSortOrder.ASC))
                .thenReturn(List.of(sampleTransactionResponse()));

        // .param(이름, 값)으로 쿼리스트링을 하나씩 붙인다.
        // 실제 요청으로 치면 GET /wallet/me/transactions?type=CREDIT&period=WEEK&sort=ASC 와 동일하다.
        var response = mockMvc.perform(get("/wallet/me/transactions")
                        .param("type", "CREDIT")
                        .param("period", "WEEK")
                        .param("sort", "ASC"))
                .andReturn().getResponse();

        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertEquals(200, response.getStatus(), body);

        // 진짜 검증 포인트: 문자열로 보낸 쿼리 파라미터가 정확히 이 3개 enum 값으로 변환되어
        // Service에 전달됐는지 확인. (지난번 고친 @RequestParam 버그가 재발하면 여기서 걸린다)
        verify(walletService).getMyTransactions(17L, TransactionTypeFilter.CREDIT, TransactionPeriod.WEEK, TransactionSortOrder.ASC);
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

    //두 테스트가 공용으로 쓸 가짜 거래내역 하나
    private WalletTransactionResponseDTO sampleTransactionResponse() {

        // WalletTransactionResponseDTO는 생성자가 private이고, VO를 받아서 DTO로 변환하는
        // 정적 팩토리 메서드 of(WalletTransactionVO)만 열려있어요 (WalletTransactionResponseDTO.java 확인함).
        // 그래서 먼저 "DB에서 읽어온 것처럼" VO를 만들고, 그걸 DTO로 감싸는 순서로 갑니다.
        WalletTransactionVO transaction = new WalletTransactionVO();
        transaction.setId(1L);
        transaction.setDirection("DEBIT");
        transaction.setAmount(3500L);
        transaction.setBalanceAfter(146500L);
        transaction.setDescription("편의점 결제");
        transaction.setCreatedAt(LocalDateTime.of(2026, 7, 31, 16, 15, 43));

        return WalletTransactionResponseDTO.of(transaction);
    }
}