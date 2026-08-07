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

    WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "지갑을 찾을 수 없습니다."),
    INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, "잔액이 부족합니다."),
    INVALID_TRANSFER_AMOUNT(HttpStatus.BAD_REQUEST, "송금 금액은 0보다 커야 합니다."),
    WALLET_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 회원 지갑이 존재합니다."),
    TRANSFER_SAME_WALLET(HttpStatus.BAD_REQUEST, "같은 지갑끼리는 송금할 수 없습니다."),
    TRANSFER_NOT_FOUND(HttpStatus.NOT_FOUND, "송금 내역을 찾을 수 없습니다."),
    IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "이미 다른 내용으로 사용된 멱등성 키입니다."),
    INVALID_WALLET_ID(HttpStatus.BAD_REQUEST, "지갑 아이디가 필요합니다."),
    INVALID_TRANSFER_TYPE(HttpStatus.BAD_REQUEST, "송금 종류가 필요합니다."),
    INVALID_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST, "멱등성 키가 필요합니다.");


    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
