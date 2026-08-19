package com.teenyfin.teenymoney.domain.report.exception;

import com.teenyfin.teenymoney.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MoneyReportErrorCode implements ErrorCode {

    MONEY_REPORT_INVALID_MONTH(
            HttpStatus.BAD_REQUEST, "조회할 월은 YYYY-MM 형식이어야 합니다."),

    // 화면은 이 코드를 받으면 현재 월로 되돌리고 안내를 띄운다.
    // 조용히 현재 월을 돌려주면 화면이 되돌릴 시점을 알 수 없다.
    MONEY_REPORT_FUTURE_MONTH(
            HttpStatus.BAD_REQUEST, "아직 오지 않은 달은 조회할 수 없습니다."),

    MONEY_REPORT_MONTH_BEFORE_JOIN(
            HttpStatus.BAD_REQUEST, "가입 이전 달은 조회할 수 없습니다."),

    MONEY_REPORT_CHILD_NOT_FOUND(
            HttpStatus.NOT_FOUND, "자녀 정보를 찾을 수 없습니다."),

    // Dify가 4xx/5xx로 응답했거나 네트워크 자체가 실패한 경우 (챗봇의 CHATBOT_REQUEST_FAILED와 동일한 역할)
    MONEY_REPORT_ANALYSIS_REQUEST_FAILED(
            HttpStatus.BAD_GATEWAY, "리포트 분석 응답을 받아오지 못했습니다. 잠시 후 다시 시도해주세요."),

    // Dify가 200을 줬는데 분석 텍스트(outputs.text)가 없거나 빈 경우
    MONEY_REPORT_ANALYSIS_RESPONSE_INVALID(
            HttpStatus.BAD_GATEWAY, "리포트 분석 응답이 올바르지 않습니다."),

    MONEY_REPORT_ANALYSIS_API_KEY_MISSING(
            HttpStatus.SERVICE_UNAVAILABLE, "리포트 분석 AI API 설정이 필요합니다.");


    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
