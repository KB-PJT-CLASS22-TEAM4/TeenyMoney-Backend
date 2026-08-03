package com.teenyfin.teenymoney.domain.wallet.exception;

import com.teenyfin.teenymoney.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 지갑 도메인 업무 에러 코드.
 */
@Getter
@RequiredArgsConstructor
public enum WalletErrorCode implements ErrorCode {

    WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "지갑을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
