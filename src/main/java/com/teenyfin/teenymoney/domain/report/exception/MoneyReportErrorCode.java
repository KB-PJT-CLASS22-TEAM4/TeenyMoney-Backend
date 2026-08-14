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
            HttpStatus.NOT_FOUND, "자녀 정보를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
