package com.teenyfin.teenymoney.domain.charge.exception;

import com.teenyfin.teenymoney.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;


@Getter
@RequiredArgsConstructor

public enum ChargeErrorCode implements ErrorCode {
    TOSS_BILLING_KEY_ISSUE_FAILED(
            HttpStatus.BAD_GATEWAY, "카드 등록에 실패했습니다."),
    TOSS_BILLING_KEY_DELETE_FAILED(
            HttpStatus.BAD_GATEWAY, "결제수단 삭제에 실패했습니다."),
    TOSS_RESPONSE_INVALID(
            HttpStatus.BAD_GATEWAY, "토스페이먼츠 응답이 올바르지 않습니다."),
    // 아래 2개는 다음 단계(ChargeMethodService)에서 쓸 건데, 파일 하나로 할 수 있다.
    CHARGE_METHOD_NOT_FOUND(
            HttpStatus.NOT_FOUND, "결제수단을 찾을 수 없습니다."),
    CHARGE_METHOD_ACCESS_DENIED(
            HttpStatus.FORBIDDEN, "본인의 결제수단만 관리할 수 있습니다."),
    BILLING_KEY_ENCRYPTION_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR, "결제수단 정보 처리 중 오류가 발생했습니다."),
    CHARGE_METHOD_SAVE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR, "결제수단 저장에 실패했습니다."),
    TOSS_CHARGE_APPROVAL_FAILED(
            HttpStatus.BAD_GATEWAY, "카드 충전 승인에 실패했습니다."),
    TOSS_CHARGE_CANCEL_FAILED(
            HttpStatus.BAD_GATEWAY, "충전 취소에 실패했습니다."),
    // 정상적인 타임아웃 재시도는 Idempotency-Key 헤더가 처리해줘서 원래 이 에러를 볼 일이 없어야 함.
    // 그런데도 보인다면 멱등키를 실수로 다르게 보낸 버그일 가능성이 큼 - 2차 안전망 용도.
    TOSS_ORDER_ID_DUPLICATED(
            HttpStatus.CONFLICT, "이미 처리된 충전 요청입니다."),
    // 같은 멱등키로 첫 요청이 아직 처리 중일 때 재시도하면 토스가 이걸 돌려줌.
    // 실패가 아니라 "아직 결과를 모름"이라, 이 상태를 실패로 기록하면 안 됨.
    TOSS_REQUEST_IN_PROGRESS(
            HttpStatus.CONFLICT, "이전 요청이 아직 처리 중입니다. 잠시 후 다시 확인해주세요."),
    CHARGE_NOT_FOUND(
            HttpStatus.NOT_FOUND, "충전 시도를 찾을 수 없습니다."),
    INVALID_CHARGE_AMOUNT(
            HttpStatus.BAD_REQUEST, "충전 금액은 0보다 커야 합니다."),
    INVALID_IDEMPOTENCY_KEY(
            HttpStatus.BAD_REQUEST, "멱등성 키가 필요합니다."),
    // idempotencyKey는 같은데 walletId/paymentMethodId/amount 중 하나라도 다른 요청이 온 경우.
    // "이 키는 이미 다른 내용으로 쓰였다"는 뜻 - TransferService의 같은 이름 에러코드와 같은 개념.
    IDEMPOTENCY_KEY_CONFLICT(
            HttpStatus.CONFLICT, "이미 다른 내용으로 사용된 멱등성 키입니다."),
    CHARGE_ALREADY_PROCESSING(
            HttpStatus.CONFLICT, "이미 처리 중인 충전입니다."),
    // DUPLICATED_ORDER_ID를 받았는데 우리 DB엔 아직 SUCCESS로 안 남아있는 이상 상태.
    // 멱등키를 실수로 다르게 보낸 버그일 가능성이 있어 운영자 확인이 필요함.
    CHARGE_STATE_CONFLICT(
            HttpStatus.CONFLICT, "충전 상태를 확인할 수 없습니다. 잠시 후 다시 시도해주세요.");


    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name(); // enum 상수 이름(예: "TOSS_BILLING_KEY_ISSUE_FAILED") 그대로가 응답의 code 값이 됨
    }
}
