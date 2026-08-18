package com.teenyfin.teenymoney.domain.report.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teenyfin.teenymoney.domain.report.dto.response.AudienceResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.AvailableMonthResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.InsightResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.MoneyReportResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.PeriodResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.SpendingCategoryResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.SpendingResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.SummaryResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.WatchSpendingResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.WeeklyTrendResponseDTO;
import com.teenyfin.teenymoney.domain.report.exception.MoneyReportErrorCode;
import com.teenyfin.teenymoney.domain.report.dto.response.ReportAnalysisResponseDTO;
import com.teenyfin.teenymoney.domain.report.service.MoneyReportService;
import com.teenyfin.teenymoney.domain.report.service.ReportAnalysisService;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.exception.CommonErrorCode;
import com.teenyfin.teenymoney.global.exception.GlobalExceptionAdvice;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * HTTP 경계 검증. 서비스는 목이다.
 *
 * 서비스 테스트는 requireChildAccess 를 불렀는지까지만 본다. 여기서는 그 위층을 본다.
 * 경로가 맞는지, @AuthenticationPrincipal 이 주입되는지, BusinessException 이 실제로
 * 어떤 HTTP 상태와 code 로 바뀌는지. 그동안 이 층은 손으로 curl 쳐서만 확인했다.
 *
 * MockMvc 의 ObjectMapper 는 Jackson2ObjectMapperBuilder 로 만든다. 운영 MVC 컨버터와
 * 같은 방식이라 날짜 직렬화가 실제와 어긋나지 않는다.
 */
class MoneyReportControllerTest {

    private static final Long CHILD_ID = 2L;
    private static final MemberPrincipal CHILD = new MemberPrincipal(CHILD_ID, "CHILD");
    private static final MemberPrincipal PARENT = new MemberPrincipal(1L, "PARENT");
    private static final MemberPrincipal OTHER_PARENT = new MemberPrincipal(9L, "PARENT");
    private static final MemberPrincipal OTHER_CHILD = new MemberPrincipal(3L, "CHILD");

    private MoneyReportService moneyReportService;
    private ReportAnalysisService reportAnalysisService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        moneyReportService = mock(MoneyReportService.class);
        reportAnalysisService = mock(ReportAnalysisService.class);
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

        // standaloneSetup은 @PreAuthorize를 실제로 강제하지 않는다(스프링 시큐리티 필터체인이
        // 없는 순수 MockMvc라서) - 그래서 이 테스트 파일에서 /reports/money/analysis의
        // "자녀만 호출 가능" 여부는 검증하지 않는다. 여기서는 컨트롤러가 서비스 호출과
        // 응답 매핑을 제대로 하는지만 본다.
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MoneyReportController(moneyReportService, reportAnalysisService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(MemberPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private String body(String url) throws Exception {
        return mockMvc.perform(get(url))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private int status(String url) throws Exception {
        return mockMvc.perform(get(url)).andReturn().getResponse().getStatus();
    }

    // ---- AI 분석 (/reports/money/analysis) -------------------------------------
    // @PreAuthorize("hasRole('CHILD')") 자체는 standaloneSetup에서 검증 안 됨 (위 setUp 주석 참고).
    // 여기서는 컨트롤러가 서비스 결과를 그대로 ApiResponse로 감싸서 내려주는지만 확인한다.

    @Test
    @DisplayName("분석 API를 호출하면 서비스가 만든 분석 텍스트를 그대로 감싸서 내려준다")
    void analyzeReturnsAnalysisFromService() throws Exception {
        authenticate(CHILD);
        when(reportAnalysisService.analyze(CHILD))
                .thenReturn(new ReportAnalysisResponseDTO("이번 달엔 용돈을 받은 날 크게 쓰는 습관이 보였어요."));

        String body = body("/reports/money/analysis");

        assertTrue(body.contains("\"success\":true"), body);
        assertTrue(body.contains("이번 달엔 용돈을 받은 날 크게 쓰는 습관이 보였어요."), body);
        verify(reportAnalysisService).analyze(CHILD);
    }

    // ---- 정상 -----------------------------------------------------------------

    @Test
    @DisplayName("자녀 본인이 조회하면 200 과 리포트가 나온다")
    void childGetsOwnReport() throws Exception {
        authenticate(CHILD);
        when(moneyReportService.getMoneyReport(eq(CHILD), eq(CHILD_ID), isNull()))
                .thenReturn(sample());

        String body = body("/reports/money/children/" + CHILD_ID);

        assertTrue(body.contains("\"success\":true"), body);
        assertTrue(body.contains("\"code\":\"OK\""), body);
        assertTrue(body.contains("\"yearMonth\":\"2026-08\""), body);
        assertTrue(body.contains("\"ageBand\":\"JUNIOR\""), body);

        verify(moneyReportService).getMoneyReport(CHILD, CHILD_ID, null);
    }

    @Test
    @DisplayName("부모가 자기 자녀를 조회하면 200 이고 자녀와 같은 응답이다")
    void parentGetsChildReport() throws Exception {
        authenticate(PARENT);
        when(moneyReportService.getMoneyReport(eq(PARENT), eq(CHILD_ID), isNull()))
                .thenReturn(sample());

        assertEquals(200, status("/reports/money/children/" + CHILD_ID));

        // 부모도 childId 를 그대로 넘긴다. 경로가 자녀용과 다르지 않다.
        verify(moneyReportService).getMoneyReport(PARENT, CHILD_ID, null);
    }

    @Test
    @DisplayName("month 를 주면 그대로 서비스에 전달된다")
    void passesMonthThrough() throws Exception {
        authenticate(CHILD);
        when(moneyReportService.getMoneyReport(any(), any(), any())).thenReturn(sample());

        mockMvc.perform(get("/reports/money/children/{id}", CHILD_ID)
                .param("month", "2026-07"));

        verify(moneyReportService).getMoneyReport(CHILD, CHILD_ID, "2026-07");
    }

    @Test
    @DisplayName("날짜는 배열이 아니라 문자열로 나가고 미도래 주차는 null 로 남는다")
    void serializesDatesAndNullWeeks() throws Exception {
        authenticate(CHILD);
        when(moneyReportService.getMoneyReport(any(), any(), any())).thenReturn(sample());

        String body = body("/reports/money/children/" + CHILD_ID);

        assertTrue(body.contains("\"startDate\":\"2026-08-01\""), body);
        assertTrue(body.contains("\"amount\":null"), body);
        assertTrue(!body.contains("[2026,"), body);
    }

    // ---- 권한 4갈래 ------------------------------------------------------------

    @Test
    @DisplayName("타 가족 부모는 403 이고 집계는 시작조차 하지 않는다")
    void otherParentIsForbidden() throws Exception {
        authenticate(OTHER_PARENT);
        when(moneyReportService.getMoneyReport(eq(OTHER_PARENT), eq(CHILD_ID), isNull()))
                .thenThrow(new BusinessException(CommonErrorCode.AUTH_FORBIDDEN));

        var response = mockMvc.perform(get("/reports/money/children/" + CHILD_ID))
                .andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);

        assertEquals(403, response.getStatus(), body);
        assertTrue(body.contains("\"success\":false"), body);
        assertTrue(body.contains("\"code\":\"AUTH_FORBIDDEN\""), body);
    }

    @Test
    @DisplayName("다른 자녀도 403 이다")
    void otherChildIsForbidden() throws Exception {
        authenticate(OTHER_CHILD);
        when(moneyReportService.getMoneyReport(eq(OTHER_CHILD), eq(CHILD_ID), isNull()))
                .thenThrow(new BusinessException(CommonErrorCode.AUTH_FORBIDDEN));

        assertEquals(403, status("/reports/money/children/" + CHILD_ID));
    }

    @Test
    @DisplayName("인증 정보가 없으면 서비스에 null principal 이 넘어가고 403 이 된다")
    void anonymousIsForbidden() throws Exception {
        // 인증 없이 호출. 운영에서는 시큐리티 필터가 먼저 막지만,
        // 컨트롤러가 principal 을 그대로 넘기고 서비스가 막는 것까지 확인한다.
        when(moneyReportService.getMoneyReport(isNull(), eq(CHILD_ID), isNull()))
                .thenThrow(new BusinessException(CommonErrorCode.AUTH_FORBIDDEN));

        assertEquals(403, status("/reports/money/children/" + CHILD_ID));
    }

    // ---- 오류 코드 -------------------------------------------------------------

    @Test
    @DisplayName("미래 월은 400 MONEY_REPORT_FUTURE_MONTH")
    void futureMonthIsBadRequest() throws Exception {
        authenticate(CHILD);
        when(moneyReportService.getMoneyReport(any(), any(), eq("2026-09")))
                .thenThrow(new BusinessException(
                        MoneyReportErrorCode.MONEY_REPORT_FUTURE_MONTH));

        var response = mockMvc.perform(get("/reports/money/children/{id}", CHILD_ID)
                        .param("month", "2026-09"))
                .andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);

        assertEquals(400, response.getStatus(), body);
        assertTrue(body.contains("\"code\":\"MONEY_REPORT_FUTURE_MONTH\""), body);
    }

    @Test
    @DisplayName("형식 오류는 400 MONEY_REPORT_INVALID_MONTH")
    void invalidMonthIsBadRequest() throws Exception {
        authenticate(CHILD);
        when(moneyReportService.getMoneyReport(any(), any(), eq("zzz")))
                .thenThrow(new BusinessException(
                        MoneyReportErrorCode.MONEY_REPORT_INVALID_MONTH));

        var response = mockMvc.perform(get("/reports/money/children/{id}", CHILD_ID)
                        .param("month", "zzz"))
                .andReturn().getResponse();

        assertEquals(400, response.getStatus());
        assertTrue(response.getContentAsString(StandardCharsets.UTF_8)
                .contains("MONEY_REPORT_INVALID_MONTH"));
    }

    @Test
    @DisplayName("가입 이전 월은 400 MONEY_REPORT_MONTH_BEFORE_JOIN")
    void monthBeforeJoinIsBadRequest() throws Exception {
        authenticate(CHILD);
        when(moneyReportService.getMoneyReport(any(), any(), eq("2020-01")))
                .thenThrow(new BusinessException(
                        MoneyReportErrorCode.MONEY_REPORT_MONTH_BEFORE_JOIN));

        assertEquals(400, mockMvc.perform(get("/reports/money/children/{id}", CHILD_ID)
                .param("month", "2020-01")).andReturn().getResponse().getStatus());
    }

    @Test
    @DisplayName("자녀를 못 찾으면 404")
    void childNotFoundIsNotFound() throws Exception {
        authenticate(CHILD);
        when(moneyReportService.getMoneyReport(any(), eq(999L), isNull()))
                .thenThrow(new BusinessException(
                        MoneyReportErrorCode.MONEY_REPORT_CHILD_NOT_FOUND));

        assertEquals(404, status("/reports/money/children/999"));
    }

    @Test
    @DisplayName("childId 가 숫자가 아니면 서비스까지 가지 않는다")
    void nonNumericChildIdDoesNotReachService() throws Exception {
        authenticate(CHILD);

        mockMvc.perform(get("/reports/money/children/abc"));

        verify(moneyReportService, never()).getMoneyReport(any(), any(), any());
    }

    // ---- 픽스처 ---------------------------------------------------------------

    private MoneyReportResponseDTO sample() {
        return new MoneyReportResponseDTO(
                new PeriodResponseDTO("2026-08",
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 14),
                        "IN_PROGRESS",
                        LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 14)),
                new AudienceResponseDTO("JUNIOR"),
                List.of(new AvailableMonthResponseDTO("2026-08", "IN_PROGRESS")),
                new SummaryResponseDTO(26000, 3, 10000, 0, 10000, 5000, 1, 0, 0, 0, 0),
                List.of(new InsightResponseDTO(
                        "SAVING_PAYMENT",
                        Map.of("amount", 10000L),
                        "/child/finance/my-products")),
                new SpendingResponseDTO(26000, 3, 17000, 2, 9000, 1,
                        List.of(new WeeklyTrendResponseDTO(4,
                                LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 23),
                                null, null)),
                        List.of(new SpendingCategoryResponseDTO(
                                3L, "문구·도서·완구", 11000, 1, 42))),
                new WatchSpendingResponseDTO(0, 0, 3, 0, List.of()),
                null);
    }
}
