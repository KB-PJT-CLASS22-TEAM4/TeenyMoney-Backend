package com.teenyfin.teenymoney.domain.allowance.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AllowanceErrorCodeTest {

    // 4개 에러코드 전부 getCode()가 enum 상수 이름과 똑같이 나오는지 (관례 확인)
    @Test
    void codeMatchesEnumNameForEveryConstant() {
        for (AllowanceErrorCode errorCode : AllowanceErrorCode.values()) {
            assertEquals(errorCode.name(), errorCode.getCode());
        }
    }

    // SCHEDULE_NOT_FOUND에 매핑된 HTTP 상태값이 404인지
    @Test
    void scheduleNotFoundIs404() {
        assertEquals(HttpStatus.NOT_FOUND, AllowanceErrorCode.SCHEDULE_NOT_FOUND.getStatus());
    }

    // SCHEDULE_ACCESS_DENIED에 매핑된 HTTP 상태값이 403인지
    @Test
    void scheduleAccessDeniedIs403() {
        assertEquals(HttpStatus.FORBIDDEN, AllowanceErrorCode.SCHEDULE_ACCESS_DENIED.getStatus());
    }

    // SCHEDULE_ALREADY_EXISTS에 매핑된 HTTP 상태값이 409인지
    @Test
    void scheduleAlreadyExistsIs409() {
        assertEquals(HttpStatus.CONFLICT, AllowanceErrorCode.SCHEDULE_ALREADY_EXISTS.getStatus());
    }

    // INVALID_PAYMENT_DAY에 매핑된 HTTP 상태값이 400인지
    @Test
    void invalidPaymentDayIs400() {
        assertEquals(HttpStatus.BAD_REQUEST, AllowanceErrorCode.INVALID_PAYMENT_DAY.getStatus());
    }
}
