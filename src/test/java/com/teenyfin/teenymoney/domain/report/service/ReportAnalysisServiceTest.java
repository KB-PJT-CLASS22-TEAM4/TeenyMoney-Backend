package com.teenyfin.teenymoney.domain.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.teenyfin.teenymoney.domain.report.dify.ReportAnalysisDifyClient;
import com.teenyfin.teenymoney.domain.report.dto.response.MoneyReportResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.PeriodResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.ReportAnalysisResponseDTO;
import com.teenyfin.teenymoney.domain.report.exception.MoneyReportErrorCode;
import com.teenyfin.teenymoney.domain.report.mapper.MoneyReportMapper;
import com.teenyfin.teenymoney.domain.report.vo.AllowanceCreditVO;
import com.teenyfin.teenymoney.domain.report.vo.DailySpendingVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReportAnalysisServiceTest {

    private static final Long CHILD_ID = 7L;

    private MoneyReportService moneyReportService;
    private MoneyReportMapper moneyReportMapper;
    private ReportAnalysisDifyClient difyClient;
    private ReportAnalysisService service;
    private MemberPrincipal principal;

    @BeforeEach
    void setUp() {
        moneyReportService = mock(MoneyReportService.class);
        moneyReportMapper = mock(MoneyReportMapper.class);
        difyClient = mock(ReportAnalysisDifyClient.class);

        // 실제로 만들어지는 JSON 내용을 검증해야 하므로 mock이 아니라 진짜 ObjectMapper를 쓴다 -
        // RedisConfig.objectMapper() 빈과 똑같이 맞춰야 한다: JavaTimeModule을 등록 안 하면
        // LocalDate에서 바로 예외가 나고, WRITE_DATES_AS_TIMESTAMPS를 안 끄면 @JsonFormat이
        // 없는 LocalDate 필드(AllowanceCreditVO.creditedOn 등)가 "2026-08-03"이 아니라
        // [2026,8,3] 배열로 나가버린다.
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 트랜잭션 매니저는 순수 목이다 - 여기서 검증하려는 건 실제 커밋/롤백 동작이 아니라
        // ReportAnalysisService가 어떤 예외를 삼키고 어떤 예외를 다시 던지는지이므로,
        // TransactionTemplate이 요구하는 인터페이스만 충족하면 충분하다.
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);

        // "오늘"을 2026-08-18로 고정한다 - YearMonth.now(clock) 결과가 테스트 실행 시점과
        // 무관하게 항상 2026-08(이번 달)/2026-07(지난달)이 되게 하기 위함.
        Clock clock = Clock.fixed(
                LocalDate.of(2026, 8, 18).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(),
                ZoneId.of("Asia/Seoul"));

        service = new ReportAnalysisService(
                moneyReportService, moneyReportMapper, difyClient, objectMapper, clock, transactionManager);

        principal = new MemberPrincipal(CHILD_ID, "CHILD");

        when(difyClient.isConfigured()).thenReturn(true);
        when(difyClient.analyze(anyString(), eq("member-" + CHILD_ID))).thenReturn("분석 결과 텍스트");
    }

    @Test
    @DisplayName("Dify API 키가 설정 안 됐으면 DB 조회 전에 바로 막는다")
    void analyzeFailsFastWhenDifyNotConfigured() {
        when(difyClient.isConfigured()).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.analyze(principal));

        assertEquals(MoneyReportErrorCode.MONEY_REPORT_ANALYSIS_API_KEY_MISSING, exception.getErrorCode());
        // API 키 체크가 DB 조회보다 먼저 있어야 한다 - 무거운 집계를 먼저 하고 나서
        // 마지막에야 503이 나면 안 된다.
        verifyNoInteractions(moneyReportService, moneyReportMapper);
    }

    @Test
    @DisplayName("이번 달과 지난달 데이터를 모아 Dify에 보내고 받은 분석 텍스트를 그대로 돌려준다")
    void analyzeCombinesBothMonthsAndReturnsDifyAnalysis() {
        MoneyReportResponseDTO thisMonthReport = reportWithPeriod(
                "2026-08", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 18));
        MoneyReportResponseDTO lastMonthReport = reportWithPeriod(
                "2026-07", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        when(moneyReportService.getMoneyReport(principal, CHILD_ID, "2026-08")).thenReturn(thisMonthReport);
        when(moneyReportService.getMoneyReport(principal, CHILD_ID, "2026-07")).thenReturn(lastMonthReport);

        when(moneyReportMapper.selectDailySpending(CHILD_ID, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 18)))
                .thenReturn(List.of(new DailySpendingVO(LocalDate.of(2026, 8, 3), 15000L, 2)));
        when(moneyReportMapper.selectAllowanceCredits(CHILD_ID, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 18)))
                .thenReturn(List.of(new AllowanceCreditVO(LocalDate.of(2026, 8, 3), 20000L)));
        when(moneyReportMapper.selectDailySpending(CHILD_ID, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)))
                .thenReturn(List.of());
        when(moneyReportMapper.selectAllowanceCredits(CHILD_ID, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)))
                .thenReturn(List.of());

        ReportAnalysisResponseDTO result = service.analyze(principal);

        assertEquals("분석 결과 텍스트", result.getAnalysis());

        // Dify에 실제로 넘어간 JSON 문자열 안에 두 달치가 다 들어있는지 확인한다 -
        // "이번 달 vs 지난달" 비교가 진짜로 이뤄지는지의 핵심 근거.
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(difyClient).analyze(jsonCaptor.capture(), eq("member-" + CHILD_ID));
        String sentJson = jsonCaptor.getValue();
        assertTrue(sentJson.contains("\"yearMonth\":\"2026-08\""));
        assertTrue(sentJson.contains("\"yearMonth\":\"2026-07\""));
        assertTrue(sentJson.contains("\"creditedOn\":\"2026-08-03\""));
    }

    @Test
    @DisplayName("지난달이 가입 이전 달이면 예외 없이 lastMonth를 null로 보내고 분석을 진행한다")
    void analyzeTreatsMonthBeforeJoinAsNullLastMonth() {
        MoneyReportResponseDTO thisMonthReport = reportWithPeriod(
                "2026-08", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 18));

        when(moneyReportService.getMoneyReport(principal, CHILD_ID, "2026-08")).thenReturn(thisMonthReport);
        when(moneyReportService.getMoneyReport(principal, CHILD_ID, "2026-07"))
                .thenThrow(new BusinessException(MoneyReportErrorCode.MONEY_REPORT_MONTH_BEFORE_JOIN));

        when(moneyReportMapper.selectDailySpending(eq(CHILD_ID), any(), any())).thenReturn(List.of());
        when(moneyReportMapper.selectAllowanceCredits(eq(CHILD_ID), any(), any())).thenReturn(List.of());

        ReportAnalysisResponseDTO result = assertDoesNotThrow(() -> service.analyze(principal));

        assertEquals("분석 결과 텍스트", result.getAnalysis());

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(difyClient).analyze(jsonCaptor.capture(), eq("member-" + CHILD_ID));
        assertTrue(jsonCaptor.getValue().contains("\"lastMonth\":null"));

        // 지난달 조회 자체가 실패했으니, 지난달 기간으로 원본 데이터를 또 조회하러 가면 안 된다
        verify(moneyReportMapper, never())
                .selectDailySpending(eq(CHILD_ID), eq(LocalDate.of(2026, 7, 1)), any());
    }

    @Test
    @DisplayName("지난달 조회가 가입 이전 달이 아닌 다른 이유로 실패하면 삼키지 않고 그대로 던진다")
    void analyzePropagatesOtherBusinessExceptionsFromLastMonth() {
        MoneyReportResponseDTO thisMonthReport = reportWithPeriod(
                "2026-08", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 18));

        when(moneyReportService.getMoneyReport(principal, CHILD_ID, "2026-08")).thenReturn(thisMonthReport);
        when(moneyReportService.getMoneyReport(principal, CHILD_ID, "2026-07"))
                .thenThrow(new BusinessException(MoneyReportErrorCode.MONEY_REPORT_CHILD_NOT_FOUND));

        when(moneyReportMapper.selectDailySpending(eq(CHILD_ID), any(), any())).thenReturn(List.of());
        when(moneyReportMapper.selectAllowanceCredits(eq(CHILD_ID), any(), any())).thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class, () -> service.analyze(principal));

        assertEquals(MoneyReportErrorCode.MONEY_REPORT_CHILD_NOT_FOUND, exception.getErrorCode());
        // 예외가 그대로 새나갔으니 Dify까지는 도달하지 않는다
        verify(difyClient, never()).analyze(any(), any());
    }

    // period만 채운 최소 리포트. period 외 필드는 이 서비스가 그대로 JSON에 실어 보내기만
    // 할 뿐 내용을 들여다보지 않으므로 null이어도 무방하다.
    private MoneyReportResponseDTO reportWithPeriod(String yearMonth, LocalDate start, LocalDate end) {
        PeriodResponseDTO period = new PeriodResponseDTO(yearMonth, start, end, "IN_PROGRESS", start, end);
        return new MoneyReportResponseDTO(period, null, null, null, null, null, null, null);
    }
}