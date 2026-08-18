package com.teenyfin.teenymoney.domain.report.service;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teenyfin.teenymoney.domain.report.dify.ReportAnalysisDifyClient;
import com.teenyfin.teenymoney.domain.report.dto.response.MoneyReportResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.ReportAnalysisResponseDTO;
import com.teenyfin.teenymoney.domain.report.exception.MoneyReportErrorCode;
import com.teenyfin.teenymoney.domain.report.mapper.MoneyReportMapper;
import com.teenyfin.teenymoney.domain.report.vo.AllowanceCreditVO;
import com.teenyfin.teenymoney.domain.report.vo.DailySpendingVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;


/**
 * "이번 달 vs 지난달" 금융 습관을 원본 그대로 Dify(LLM)에 넘겨 서술형 조언을 받아온다.
 *
 * MoneyReportService가 화면용으로 이미 정리해둔 두 달치 리포트에, 화면엔 없는 원본
 * 자료(일자별 소비, 정기 용돈 입금일)를 더해서 넘긴다.
 * 결과는 저장하지 않는다 - 호출할 때마다 매번 새로 만든다 (캐싱/영속화는 의도적으로 이번 범위 밖).
 */

// "자녀가 API를 호출하면 → 이번 달·지난달 데이터를 각각 모아서 → 하나의 JSON 문자열로 합친 뒤 → Dify에 보내고 → Dify가 준 분석 텍스트를 그대로 돌려준다"
@Service
public class ReportAnalysisService {
    private final MoneyReportService moneyReportService;
    private final MoneyReportMapper moneyReportMapper;
    private final ReportAnalysisDifyClient difyClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ReportAnalysisService(MoneyReportService moneyReportService, MoneyReportMapper moneyReportMapper, ReportAnalysisDifyClient difyClient, ObjectMapper objectMapper, Clock clock) {
        this.moneyReportService = moneyReportService;
        this.moneyReportMapper = moneyReportMapper;
        this.difyClient = difyClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }
// 트랜잭션 사용 이유는 중간에 다른 트랜잭션이 끼어들어 숫자가 안맞는 일을 막기 위함
    @Transactional(readOnly = true)
    public ReportAnalysisResponseDTO analyze(MemberPrincipal principal) {
        Long childId = principal.memberId();

        YearMonth thisMonth = YearMonth.now(clock);
        YearMonth lastMonth = thisMonth.minusMonths(1);

        // "이번 달 vs 지난달" 비교의 실체 - 같은 collect()를 월만 바꿔 두 번 부른다.
        MonthlyHabitData thisMonthData = collect(principal, childId, thisMonth);
        MonthlyHabitData lastMonthData = collect(principal, childId, lastMonth);

        // 두 달치를 JSON 문자열 하나로 뭉친 뒤 Dify로 보내고, 서술형 분석 텍스트를 그대로 받아온다.
        String reportDataJson = serialize(thisMonthData, lastMonthData);
        String analysis = difyClient.analyze(reportDataJson, "member-" + childId);

        return new ReportAnalysisResponseDTO(analysis);
    }

    /**
     * 한 달치 자료를 모은다. MoneyReportService.getMoneyReport()가 이미 계산해둔
     * period.startDate/endDate를 그대로 재사용해서, 진행 중인 달이면 "오늘까지만"
     * 잘리는 규칙(MoneyReportService의 핵심 원칙)이 원본 데이터 쪽에도 자연히 맞춰진다 -
     * 여기서 기간을 따로 다시 계산하면 화면 리포트와 어긋날 여지가 생긴다.
     */
    private MonthlyHabitData collect(MemberPrincipal principal, Long childId, YearMonth month) {

        MoneyReportResponseDTO report = moneyReportService.getMoneyReport(principal, childId, month.toString());

        LocalDate from = report.getPeriod().getStartDate();
        LocalDate to = report.getPeriod().getEndDate();

        List<DailySpendingVO> dailySpending = moneyReportMapper.selectDailySpending(childId, from, to);
        List<AllowanceCreditVO> allowanceCredits = moneyReportMapper.selectAllowanceCredits(childId, from, to);

        return new MonthlyHabitData(month.toString(), report, dailySpending, allowanceCredits);

    }

    // 이번 달/지난달 두 MonthlyHabitData를 ReportAnalysisPayload로 감싼 뒤 JSON 문자열로 직렬화
    private String serialize(MonthlyHabitData thisMonth, MonthlyHabitData lastMonth) {

        try{
            return objectMapper.writeValueAsString(new ReportAnalysisPayload(thisMonth, lastMonth));
        } catch(JsonProcessingException exception ) {
            // 우리가 만든 DTO/record들의 조합이라 직렬화가 실패할 일이 사실상 없다 -
            // 방어적으로만 처리하고, 바깥에서 보기엔 "분석 요청을 못 보냈다"는 점에서
            // Dify 호출 실패와 다를 게 없으므로 같은 에러코드를 쓴다.
            throw new BusinessException(MoneyReportErrorCode.MONEY_REPORT_ANALYSIS_REQUEST_FAILED);
        }

    }

    // Dify에 보낼 JSON의 뼈대. API 응답 DTO가 아니라 이 서비스 안에서만 쓰는 직렬화 전용 타입.
    private record ReportAnalysisPayload(MonthlyHabitData thisMonth, MonthlyHabitData lastMonth) {
    }

    //한 달치 분석 재료를 한 덩어리로 묶는 상자 record가 없으면 collect()가 값 3개를 따로따로 리턴해야 하는데 이걸 한덩어리로 묶기 위함
    private record MonthlyHabitData(
            String yearMonth,
            MoneyReportResponseDTO report,
            List<DailySpendingVO> dailySpending,
            List<AllowanceCreditVO> allowanceCredits) {
    }

}
