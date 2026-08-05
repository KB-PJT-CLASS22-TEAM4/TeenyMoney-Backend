package com.teenyfin.teenymoney.domain.family.exception;

import com.teenyfin.teenymoney.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FamilyErrorCode implements ErrorCode {

    FAMILY_LINK_CODE_INVALID(HttpStatus.BAD_REQUEST, "만료되었거나 유효하지 않은 연동 코드입니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
