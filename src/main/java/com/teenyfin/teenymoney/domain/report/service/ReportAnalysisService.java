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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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

    // DB 조회 구간만 트랜잭션으로 묶기 위한 프로그래밍 방식 트랜잭션. 클래스 전체에
    // @Transactional을 붙이면 Dify 호출(최대 120초 걸릴 수 있는 외부 HTTP 요청)까지
    // 같은 트랜잭션 = 같은 DB 커넥션 안에 들어가버려서, 이걸 분리하려고 도입했다.
    private final TransactionTemplate transactionTemplate;

    public ReportAnalysisService(MoneyReportService moneyReportService, MoneyReportMapper moneyReportMapper, ReportAnalysisDifyClient difyClient, ObjectMapper objectMapper, Clock clock, PlatformTransactionManager transactionManager) {
        this.moneyReportService = moneyReportService;
        this.moneyReportMapper = moneyReportMapper;
        this.difyClient = difyClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setReadOnly(true);
    }

    public ReportAnalysisResponseDTO analyze(MemberPrincipal principal) {

        if (!difyClient.isConfigured()) {
            throw new BusinessException(MoneyReportErrorCode.MONEY_REPORT_ANALYSIS_API_KEY_MISSING);
        }
        Long childId = principal.memberId();


        YearMonth thisMonth = YearMonth.now(clock);
        YearMonth lastMonth = thisMonth.minusMonths(1);

        // === 트랜잭션 구간 시작 ===
        // 이번 달 + 지난달 데이터를 모으는 동안만 DB 커넥션을 잡는다. execute()가 리턴하는
        // 순간(즉 이 블록이 끝나는 순간) 트랜잭션이 커밋되고 커넥션이 커넥션 풀로 반납된다.
        ReportAnalysisPayload payload = transactionTemplate.execute(status -> {
            MonthlyHabitData thisMonthData = collect(principal, childId, thisMonth);
            // 지난달은 가입 첫 달인 경우 존재하지 않을 수 있다 - 그럴 땐 null로 넘어간다.
            MonthlyHabitData lastMonthData = collectIfAvailable(principal, childId, lastMonth);
            return new ReportAnalysisPayload(thisMonthData, lastMonthData);
        });

        String reportDataJson = serialize(payload);

        // Dify가 아무리 느려도(최대 120초) 여기선 DB 커넥션을 붙잡고 있지 않으므로
        // 다른 요청들이 커넥션 풀을 못 써서 같이 멈추는 일이 없다.
        String analysis = difyClient.analyze(reportDataJson, "member-" + childId);

        return new ReportAnalysisResponseDTO(analysis);
    }

    private MonthlyHabitData collectIfAvailable(MemberPrincipal principal, Long childId, YearMonth month) {
        try {
            return collect(principal, childId, month);
        }catch (BusinessException e) {
            if (e.getErrorCode() == MoneyReportErrorCode.MONEY_REPORT_MONTH_BEFORE_JOIN) {
                return null;
            }
            throw e;
        }
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
    private String serialize(ReportAnalysisPayload payload) {

        try{
            return objectMapper.writeValueAsString(payload);
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
