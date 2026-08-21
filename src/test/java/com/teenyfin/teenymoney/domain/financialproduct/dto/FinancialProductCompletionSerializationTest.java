package com.teenyfin.teenymoney.domain.financialproduct.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.DepositCompletionPeriodResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.FinancialProductCompletionDetailResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.LoanCompletionRepaymentResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinancialProductCompletionSerializationTest {
    // 운영 MVC 메시지 컨버터와 같은 빌더를 사용해 실제 HTTP JSON 형태를 검증한다.
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    @Test
    @DisplayName("완료 상세의 날짜와 시각은 ISO 문자열로 직렬화된다")
    void completionDatesAreSerializedAsIsoStrings() throws Exception {
        FinancialProductCompletionDetailResponseDTO response =
                FinancialProductCompletionDetailResponseDTO.builder()
                        .productType(FinancialProductType.DEPOSIT)
                        .startDate(LocalDate.of(2025, 8, 21))
                        .maturityDate(LocalDate.of(2026, 8, 21))
                        .completedAt(LocalDateTime.of(2026, 8, 21, 2, 28, 18))
                        .depositPeriods(List.of(new DepositCompletionPeriodResponseDTO(
                                1, LocalDate.of(2025, 9, 21), 50_000L, 174L, 50_174L)))
                        .savingPayments(List.of())
                        .loanRepayments(List.of())
                        .build();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertEquals("2025-08-21", json.get("startDate").asText());
        assertEquals("2026-08-21", json.get("maturityDate").asText());
        assertEquals("2026-08-21T02:28:18", json.get("completedAt").asText());
        assertEquals("2025-09-21",
                json.get("depositPeriods").get(0).get("periodEndDate").asText());
    }

    @Test
    @DisplayName("조기상환의 내부 0회차는 API에서 null로 직렬화된다")
    void earlyRepaymentInstallmentIsSerializedAsNull() throws Exception {
        LoanCompletionRepaymentResponseDTO repayment =
                new LoanCompletionRepaymentResponseDTO(
                        null, "EARLY", null, 50_002L, 50_002L,
                        0L, 0L, "PAID", null,
                        LocalDateTime.of(2026, 8, 21, 2, 34, 58),
                        LocalDateTime.of(2026, 8, 21, 2, 34, 58));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(repayment));

        assertTrue(json.get("installmentNo").isNull());
        assertEquals("2026-08-21T02:34:58", json.get("paidAt").asText());
    }
}
