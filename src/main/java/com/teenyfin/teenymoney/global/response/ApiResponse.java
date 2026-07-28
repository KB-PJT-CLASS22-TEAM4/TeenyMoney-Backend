package com.teenyfin.teenymoney.global.response;

import com.teenyfin.teenymoney.global.exception.ErrorCode;
import lombok.Getter;

/**
 * 모든 API의 응답 껍데기. 성공이든 실패든 바깥 모양이 같고 data만 달라진다.
 *
 *   성공  {"success":true,"code":"OK","message":"성공","data":{...}}
 *   실패  {"success":false,"code":"WALLET_INSUFFICIENT_BALANCE","message":"잔액이 부족합니다.","data":null}
 *
 * FE는 success 하나만 보고 분기하면 되고, 실패 시 message를 그대로 노출하면 된다.
 */
@Getter
public class ApiResponse<T> {

    private static final String SUCCESS_CODE = "OK";
    private static final String SUCCESS_MESSAGE = "성공";

    private final boolean success;
    private final String code;
    private final String message;
    private final T data;

    private ApiResponse(boolean success, String code, String message, T data) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, SUCCESS_CODE, SUCCESS_MESSAGE, null);
    }

    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return new ApiResponse<>(false, errorCode.getCode(), errorCode.getMessage(), null);
    }

    /** 사유를 구체적으로 알려줘야 할 때 (예: 어느 필드가 왜 틀렸는지) */
    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message, T data) {
        return new ApiResponse<>(false, errorCode.getCode(), message, data);
    }
}
