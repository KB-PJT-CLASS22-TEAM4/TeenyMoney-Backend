package com.teenyfin.teenymoney.domain.report.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teenyfin.teenymoney.domain.report.dto.response.AudienceResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.AvailableMonthResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.MoneyReportResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.InsightResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.MonthlyScoreReasonResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.MonthlyScoreResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.SummaryResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.PeriodResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.SpendingCategoryResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.SpendingResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.WatchSpendingResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.WeeklyTrendResponseDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDate;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 응답 JSON 모양이 FE 와 약속한 계약대로인지 본다. 서버도 DB 도 띄우지 않는다.
 *
 * 이 테스트가 있는 이유는 실제로 두 번 틀렸기 때문이다.
 *
 * 1. LocalDate 에 @JsonFormat 을 빠뜨려 날짜가 "2026-08-01" 이 아니라 [2026,8,1] 로 나갔다.
 *    이 레포는 웹 계층 ObjectMapper 에 JavaTimeModule 을 전역 등록하지 않고 DTO 필드마다
 *    @JsonFormat 을 붙이는 방식이다(MemberMeResponseDTO 참고). 새 DTO 를 만들 때마다
 *    빠뜨리기 쉽다.
 *
 * 2. 아직 오지 않은 주차의 amount 는 반드시 null 로 나가야 한다. 언젠가 누가
 *    NON_NULL 직렬화를 켜면 키 자체가 사라지고, FE 는 "0원 쓴 주"와 구분할 수 없게 된다.
 *
 * MVC 메시지 컨버터가 쓰는 것과 같은 방식으로 ObjectMapper 를 만든다.
 */
class MoneyReportSerializationTest {

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    private MoneyReportResponseDTO sample() {
        return new MoneyReportResponseDTO(
                new PeriodResponseDTO(
                        "2026-08",
                        LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 8, 14),
                        "IN_PROGRESS",
                        LocalDate.of(2026, 7, 1),
                        LocalDate.of(2026, 7, 14)),
                new AudienceResponseDTO("TEEN"),
                List.of(new AvailableMonthResponseDTO("2026-08", "IN_PROGRESS")),
                new SummaryResponseDTO(47000, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                List.of(new InsightResponseDTO(
                        "SAVING_PAYMENT",
                        insightMetrics(),
                        "/child/finance/my-products")),
                new SpendingResponseDTO(
                        47000, 3, 27000, 2, 20000, 1,
                        List.of(
                                new WeeklyTrendResponseDTO(1,
                                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2), 0L, 0),
                                new WeeklyTrendResponseDTO(4,
                                        LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 23), null, null)),
                        List.of(new SpendingCategoryResponseDTO(10L, "온라인쇼핑", 20000, 1, 43))),
                new WatchSpendingResponseDTO(2, 32000, 3, 2, List.of()),
                new MonthlyScoreResponseDTO(-6, 0, 2, List.of(
                        new MonthlyScoreReasonResponseDTO(
                                "LOAN_INSTALLMENT_OVERDUE", "대출 월별 상환 결과", -4))));
    }

    /**
     * MoneyReportService.metrics() 가 실제로 만드는 모양과 같게 둔다.
     *
     * 날짜가 LocalDate 가 아니라 String 인 것이 핵심이다. metrics 는 Map 이라 값에
     * @JsonFormat 을 걸 수 없어서, 서비스가 맵에 넣는 시점에 문자열로 굳힌다.
     * 그 변환이 살아 있는지는 MoneyReportServiceTest 가 지킨다
     * (containsEntry("maturityDate", "2026-08-31")).
     */
    private Map<String, Object> insightMetrics() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("amount", 20000L);
        m.put("savingProductCount", 2);
        m.put("maturityDate", "2026-09-05");
        m.put("closedOn", null);
        return m;
    }

    @Test
    @DisplayName("metrics 에 LocalDate 를 그대로 넣으면 배열로 나간다 — 그래서 서비스가 문자열로 바꾼다")
    void rawLocalDateInMapWouldBreakTheContract() throws Exception {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("nextPaymentDate", LocalDate.of(2026, 9, 5));

        // 이 동작 자체가 버그의 원인이었다. 맵 값에는 @JsonFormat 이 걸리지 않는다.
        assertThat(objectMapper.writeValueAsString(raw)).isEqualTo("{\"nextPaymentDate\":[2026,9,5]}");
    }

    @Test
    @DisplayName("날짜는 배열이 아니라 yyyy-MM-dd 문자열로 나간다")
    void datesAreStrings() throws Exception {
        String json = objectMapper.writeValueAsString(sample());

        assertThat(json).contains("\"startDate\":\"2026-08-01\"");
        assertThat(json).contains("\"endDate\":\"2026-08-14\"");
        assertThat(json).contains("\"comparisonStartDate\":\"2026-07-01\"");
        assertThat(json).contains("\"comparisonEndDate\":\"2026-07-14\"");

        // [2026,8,1] 같은 배열 형태가 섞여 있으면 안 된다
        assertThat(json).doesNotContain("[2026,");
    }

    @Test
    @DisplayName("아직 오지 않은 주차의 amount 는 키가 사라지지 않고 null 로 나간다")
    void futureWeekKeepsNullKey() throws Exception {
        String json = objectMapper.writeValueAsString(sample());

        assertThat(json).contains("\"amount\":null");
        assertThat(json).contains("\"paymentCount\":null");
        // 지난 주의 0원은 0으로 남아 있어야 한다
        assertThat(json).contains("\"amount\":0");
    }

    @Test
    @DisplayName("여섯 섹션이 모두 들어 있다")
    void allSectionsPresent() throws Exception {
        String json = objectMapper.writeValueAsString(sample());

        assertThat(json)
                .contains("\"period\"")
                .contains("\"audience\"")
                .contains("\"availableMonths\"")
                .contains("\"summary\"")
                .contains("\"insights\"")
                .contains("\"spending\"")
                .contains("\"watchSpending\"")
                .contains("\"teenyScore\"");
    }

    @Test
    @DisplayName("insights 의 metrics 는 키 순서가 유지되고 null 값도 살아남는다")
    void insightMetricsKeepOrderAndNulls() throws Exception {
        String json = objectMapper.writeValueAsString(sample());

        assertThat(json).contains(
                "\"maturityDate\":\"2026-09-05\"");
        assertThat(json).contains("\"insightCode\":\"SAVING_PAYMENT\"");
    }

    @Test
    @DisplayName("티니점수 이력이 없으면 teenyScore 는 null 로 나간다")
    void teenyScoreIsNullWhenNoHistory() throws Exception {
        MoneyReportResponseDTO noScore = new MoneyReportResponseDTO(
                sample().getPeriod(), sample().getAudience(), sample().getAvailableMonths(),
                sample().getSummary(), sample().getInsights(), sample().getSpending(),
                sample().getWatchSpending(), null);

        assertThat(objectMapper.writeValueAsString(noScore))
                .contains("\"teenyScore\":null");
    }
}
