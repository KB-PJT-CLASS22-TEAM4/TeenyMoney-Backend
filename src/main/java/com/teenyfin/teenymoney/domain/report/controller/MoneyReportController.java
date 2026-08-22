package com.teenyfin.teenymoney.domain.report.controller;

import com.teenyfin.teenymoney.domain.report.dto.response.MoneyReportResponseDTO;
import com.teenyfin.teenymoney.domain.report.exception.MoneyReportErrorCode;
import com.teenyfin.teenymoney.domain.report.service.MoneyReportService;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.teenyfin.teenymoney.domain.report.dto.response.ReportAnalysisResponseDTO;
import com.teenyfin.teenymoney.domain.report.service.ReportAnalysisService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.context.request.async.DeferredResult;

@RestController
@RequestMapping("/reports")
@Api(tags = "Report", description = "머니 리포트 API")
public class MoneyReportController {

    private static final long DIFY_DEFERRED_RESULT_TIMEOUT_MILLISECONDS = 135_000L;

    private final MoneyReportService moneyReportService;
    private final ReportAnalysisService reportAnalysisService;
    private final ThreadPoolTaskExecutor difyTaskExecutor;

    public MoneyReportController(MoneyReportService moneyReportService, ReportAnalysisService reportAnalysisService, @Qualifier("difyTaskExecutor") ThreadPoolTaskExecutor difyTaskExecutor) {
        this.moneyReportService = moneyReportService;
        this.reportAnalysisService = reportAnalysisService;
        this.difyTaskExecutor = difyTaskExecutor;
    }

    @GetMapping("/money/children/{childId}")
    @ApiOperation(
            value = "월간 머니 리포트 조회",
            notes = "한 달 치 리포트 데이터를 한 번에 돌려줍니다.\n\n"
                    + "자녀 본인과 그 자녀의 부모가 조회할 수 있습니다. 자녀는 자기 memberId를, "
                    + "부모는 자녀 관리 화면의 childId를 그대로 넣으면 됩니다.\n\n"
                    + "month를 생략하면 현재 월입니다. 날짜는 Asia/Seoul 기준입니다.\n\n"
                    + "진행 중인 달은 1일부터 오늘까지이고 전월의 같은 일수와 비교합니다. "
                    + "완료된 달은 1일부터 말일까지이고 직전 달 전체와 비교합니다.\n\n"
                    + "weeklyTrend는 그 달의 모든 주차를 담습니다. 주차는 월요일에 시작해 "
                    + "일요일에 끝나며 1일과 말일에서만 잘립니다. 아직 오지 않은 주차는 "
                    + "amount와 paymentCount가 null입니다. 0원을 쓴 주와 구분하기 위해서입니다.\n\n"
                    + "활동이 없는 달은 오류가 아니라 0과 빈 배열로 응답합니다.",
            authorizations = { @Authorization(value = "JWT") })
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "조회 성공"),
            @io.swagger.annotations.ApiResponse(
                    code = 400,
                    message = "month 형식 오류, 미래 월, 가입 이전 월"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "로그인 필요"),
            @io.swagger.annotations.ApiResponse(
                    code = 403,
                    message = "본인도 아니고 그 자녀의 부모도 아님"),
            @io.swagger.annotations.ApiResponse(code = 404, message = "자녀를 찾을 수 없음") })
    public ApiResponse<MoneyReportResponseDTO> getMoneyReport(
            @AuthenticationPrincipal MemberPrincipal principal,
            @ApiParam(value = "리포트 대상 자녀의 회원 아이디", required = true)
            @PathVariable Long childId,
            @ApiParam(value = "조회할 월(yyyy-MM). 생략하면 현재 월", example = "2026-08")
            @RequestParam(required = false) String month) {

        return ApiResponse.ok(
                moneyReportService.getMoneyReport(principal, childId, month));
    }

    @GetMapping("/money/analysis")
    @PreAuthorize("hasRole('CHILD')") // 자녀 전용 - 부모는 아예 호출 자체가 403
    @ApiOperation(
            value = "이번 달·지난달 금융 습관 AI 분석 및 행동 조언",
            notes = "자녀 본인의 이번 달·지난달 데이터를 그대로 Dify(LLM)에 보내 분석과 조언을 받아옵니다.\n\n"
                    + "자녀 전용 API입니다. 호출할 때마다 매번 새로 분석하며 결과를 서버에 저장하지 않습니다.",
            authorizations = { @Authorization(value = "JWT") })
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "분석 성공"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "로그인 필요"),
            @io.swagger.annotations.ApiResponse(code = 403, message = "자녀 전용 API"),
            @io.swagger.annotations.ApiResponse(code = 502, message = "Dify 분석 요청 실패"),
            @io.swagger.annotations.ApiResponse(code = 503, message = "요청이 많아 서버가 혼잡함 / Dify API 설정 누락") })
    public DeferredResult<ApiResponse<ReportAnalysisResponseDTO>> analyzeReport(
            @AuthenticationPrincipal MemberPrincipal principal) {

        // DeferredResult: "지금 당장은 못 채우지만, 나중에 다른 스레드가 채워줄 빈 상자".
        // 이 객체를 만드는 시점엔 아직 아무 일도 안 일어난다 - 그냥 상자를 하나 준비한 것뿐.
        // 이 메서드가 이 상자를 리턴하면(맨 아래 return), 그 순간 Tomcat 스레드는 "이 요청은
        // 아직 안 끝났고, 나중에 누가 이 상자를 채우면 그때 응답을 보내라"고 스프링한테 맡기고
        // 자기 할 일은 끝냈다는 듯이 빠져나간다 (= Tomcat 스레드가 반납됨).
        DeferredResult<ApiResponse<ReportAnalysisResponseDTO>> deferredResult = new DeferredResult<>(DIFY_DEFERRED_RESULT_TIMEOUT_MILLISECONDS);


        // onTimeout(): "이 상자가 125초 안에 안 채워지면 이 콜백을 대신 실행해라"고 미리 등록해두는 것.
        // 콜백 자체는 지금 실행되는 게 아니라, 나중에 진짜로 125초가 지났을 때만 스프링이 호출한다.
        // 여기서 setErrorResult()로 상자를 "예외로" 채우면, 스프링이 이 예외를 마치 컨트롤러
        // 메서드가 직접 던진 것처럼 취급해서 GlobalExceptionAdvice로 넘겨준다.
        deferredResult.onTimeout(() -> deferredResult.setErrorResult(new BusinessException(MoneyReportErrorCode.MONEY_REPORT_ANALYSIS_REQUEST_FAILED)));


        try {
            // difyTaskExecutor: DifyConfig.java에서 만든 "Dify 호출 전담" 전용 스레드풀
            // (core=2, max=4, queue=8). execute()에 넘긴 () -> {...} 부분(람다)은 지금
            // 이 자리에서 실행되는 게 아니다 - "이 작업을 나중에 이 풀 안의 스레드 중 하나가
            // 대신 실행해줘"라고 작업을 등록만 하는 것이다. execute()는 등록만 하고 즉시 리턴한다
            // (풀 안의 실제 작업이 다 끝날 때까지 여기서 기다리지 않는다 - 그게 이 방식의 핵심).
            difyTaskExecutor.execute(() -> {

                // ↓↓↓ 이 블록 안의 코드는 Tomcat 스레드가 아니라 difyTaskExecutor가 관리하는
                // "dify-1", "dify-2" 같은 별도 스레드 위에서, 나중에(등록된 후 곧) 실행된다.
                try {

                    // 여기서 실제로 Dify에 HTTP 요청을 보내고 응답이 올 때까지 기다린다
                    // (최대 120초, difyRestTemplate의 readTimeout). 이 "기다림"이 일어나는
                    // 스레드가 바로 방금 말한 dify-N 스레드 - Tomcat 스레드는 이미 반납된 뒤라
                    // 이 기다림과 아무 상관이 없다.
                    ReportAnalysisResponseDTO response = reportAnalysisService.analyze(principal);

                    // Dify 응답을 성공적으로 받았다 - 아까 만들어둔 "빈 상자"를 이제 진짜 결과로 채운다.
                    // 이 setResult() 호출이 트리거가 되어, 스프링이 그제서야 클라이언트에게
                    // 실제 HTTP 응답(200 + 이 데이터)을 내보낸다. (지금까지는 클라이언트도
                    // 계속 대기 중인 상태였다 - HTTP 연결 자체는 계속 열려있었음.)
                    deferredResult.setResult(ApiResponse.ok(response));
                } catch (Exception e) {

                    // reportAnalysisService.analyze()가 던진 예외(BusinessException 등)를
                    // 상자에 "예외로" 채운다 - onTimeout과 마찬가지로 GlobalExceptionAdvice가
                    // 동기 호출 때와 똑같은 방식으로 처리해준다.
                    deferredResult.setErrorResult(e);
                }
            });
        } catch (TaskRejectedException e) {
            // 주의: 이 catch는 위 difyTaskExecutor.execute() "호출 자체"가 실패했을 때만 걸린다 -
            // 즉 풀(최대 4개)도 다 차 있고 큐(최대 8개)도 다 차 있어서, 작업을 등록조차
            // 못 시킨 경우다. 이건 아직 Tomcat 스레드 위에서 실행 중인 동기 코드라서
            // (아직 return deferredResult를 안 했으니까) 여기서 바로 잡을 수 있다.
            throw new BusinessException(MoneyReportErrorCode.MONEY_REPORT_ANALYSIS_SERVER_BUSY);
        }

        // 이 지점에서 Tomcat 스레드가 실제로 반납된다. 위에서 등록한 difyTaskExecutor의 작업은
        // 아직 안 끝났을 수도 있다(보통 아직 안 끝났음) - 그래도 상관없이 이 메서드는 여기서
        // 끝나고, 나머지는 전부 나중에 dify-N 스레드가 setResult/setErrorResult를 호출하는
        // 순간(또는 125초 타임아웃)에 마무리된다.

        return deferredResult;
    }
}
