package com.teenyfin.teenymoney.domain.teenyscore.exception;

import com.teenyfin.teenymoney.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TeenyScoreErrorCode implements ErrorCode {

    TEENY_SCORE_CHILD_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "자녀 정보를 찾을 수 없습니다."),

    TEENY_SCORE_GRADE_NOT_FOUND(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "티니점수 등급 정보를 확인할 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
