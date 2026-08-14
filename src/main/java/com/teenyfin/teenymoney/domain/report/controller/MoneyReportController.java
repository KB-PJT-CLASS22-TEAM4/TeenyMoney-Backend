package com.teenyfin.teenymoney.domain.report.controller;

import com.teenyfin.teenymoney.domain.report.dto.response.MoneyReportResponseDTO;
import com.teenyfin.teenymoney.domain.report.service.MoneyReportService;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reports")
@Api(tags = "Report", description = "머니 리포트 API")
public class MoneyReportController {

    private final MoneyReportService moneyReportService;

    public MoneyReportController(MoneyReportService moneyReportService) {
        this.moneyReportService = moneyReportService;
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
}
