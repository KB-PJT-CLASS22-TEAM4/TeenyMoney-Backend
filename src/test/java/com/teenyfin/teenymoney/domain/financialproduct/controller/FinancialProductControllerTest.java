package com.teenyfin.teenymoney.domain.financialproduct.controller;

import com.teenyfin.teenymoney.domain.financialproduct.dto.response.FinancialProductDetailResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.FinancialProductEnrollmentListResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.FinancialProductListResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.service.FinancialProductService;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductType;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class FinancialProductControllerTest {

    private static final MemberPrincipal CHILD =
            new MemberPrincipal(2L, "CHILD");

    private FinancialProductService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(FinancialProductService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new FinancialProductController(service))
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        CHILD, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getProductsReturnsApiEnvelope() throws Exception {
        FinancialProductListResponseDTO item =
                FinancialProductListResponseDTO.builder()
                        .productId(1L)
                        .productType(FinancialProductType.DEPOSIT)
                        .productName("정기예금")
                        .eligible(true)
                        .availableTerms(List.of(12))
                        .rates(List.of())
                        .build();
        when(service.getProducts(CHILD)).thenReturn(List.of(item));

        var response = mockMvc.perform(get("/financial-products"))
                .andReturn().getResponse();

        String body = response.getContentAsString();
        assertEquals(200, response.getStatus(), body);
        assertTrue(body.contains("\"success\":true"), body);
        assertTrue(body.contains("\"productType\":\"DEPOSIT\""), body);
        verify(service).getProducts(CHILD);
    }

    @Test
    void getDepositProductsReturnsOnlyDepositServiceResult() throws Exception {
        FinancialProductListResponseDTO deposit =
                FinancialProductListResponseDTO.builder()
                        .productId(1L)
                        .productType(FinancialProductType.DEPOSIT)
                        .productName("Deposit")
                        .eligible(true)
                        .availableTerms(List.of(12))
                        .rates(List.of())
                        .build();
        when(service.getDepositProducts(CHILD)).thenReturn(List.of(deposit));

        var response = mockMvc.perform(get("/financial-products/deposit"))
                .andReturn().getResponse();

        String body = response.getContentAsString();
        assertEquals(200, response.getStatus(), body);
        assertTrue(body.contains("\"productType\":\"DEPOSIT\""), body);
        assertFalse(body.contains("\"productType\":\"SAVING\""), body);
        assertFalse(body.contains("\"productType\":\"LOAN\""), body);
        verify(service).getDepositProducts(CHILD);
        verify(service, never()).getProducts(CHILD);
    }

    @Test
    void getDepositDetailUsesSeparatedEndpoint() throws Exception {
        FinancialProductDetailResponseDTO detail =
                FinancialProductDetailResponseDTO.builder()
                        .productId(7L)
                        .productType(FinancialProductType.DEPOSIT)
                        .productName("Deposit")
                        .eligible(true)
                        .availableTerms(List.of(1, 3, 6, 12))
                        .rates(List.of())
                        .build();
        when(service.getDepositProductDetail(CHILD, 7L))
                .thenReturn(detail);

        var response = mockMvc.perform(
                        get("/financial-products/deposit/7"))
                .andReturn().getResponse();

        String body = response.getContentAsString();
        assertEquals(200, response.getStatus(), body);
        assertTrue(body.contains("\"productType\":\"DEPOSIT\""), body);
        verify(service).getDepositProductDetail(CHILD, 7L);
    }

    @Test
    void parentGetsProductsByChildId() throws Exception {
        MemberPrincipal parent = new MemberPrincipal(1L, "PARENT");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        parent, null, List.of()));
        FinancialProductEnrollmentListResponseDTO enrollment =
                FinancialProductEnrollmentListResponseDTO.builder()
                        .enrollmentId(11L)
                        .productId(1L)
                        .productType(FinancialProductType.DEPOSIT)
                        .productName("Deposit")
                        .description("Deposit description")
                        .status("ACTIVE")
                        .appliedRate(new BigDecimal("4.50"))
                        .currentAmount(100_000L)
                        .startDate(LocalDate.of(2026, 8, 1))
                        .build();
        when(service.getProductsByChildId(parent, 2L))
                .thenReturn(List.of(enrollment));

        var response = mockMvc.perform(
                        get("/financial-products/children/2"))
                .andReturn().getResponse();

        assertEquals(200, response.getStatus(),
                response.getContentAsString());
        assertTrue(response.getContentAsString()
                .contains("\"enrollmentId\":11"));
        assertTrue(response.getContentAsString()
                .contains("\"appliedRate\":4.50"));
        assertTrue(response.getContentAsString()
                .contains("\"description\":\"Deposit description\""));
        assertTrue(response.getContentAsString()
                .contains("\"startDate\":\"2026-08-01\""));
        verify(service).getProductsByChildId(parent, 2L);
    }
}
