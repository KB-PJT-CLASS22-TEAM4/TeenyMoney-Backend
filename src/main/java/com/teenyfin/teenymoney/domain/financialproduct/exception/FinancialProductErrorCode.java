package com.teenyfin.teenymoney.domain.financialproduct.exception;

import com.teenyfin.teenymoney.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FinancialProductErrorCode implements ErrorCode {
    FINANCIAL_PRODUCT_NOT_FOUND(
            HttpStatus.NOT_FOUND, "금융상품을 찾을 수 없습니다."),
    FINANCIAL_PRODUCT_ENROLLMENT_NOT_FOUND(
            HttpStatus.NOT_FOUND, "자녀의 금융상품 가입 계약을 찾을 수 없습니다."),
    FINANCIAL_PRODUCT_TYPE_INVALID(
            HttpStatus.BAD_REQUEST, "지원하지 않는 금융상품 유형입니다."),
    FINANCIAL_PRODUCT_ROLE_FORBIDDEN(
            HttpStatus.FORBIDDEN, "부모 또는 자녀 회원만 금융상품을 조회할 수 있습니다."),
    FINANCIAL_PRODUCT_PARENT_ONLY(
            HttpStatus.FORBIDDEN, "부모 회원만 자녀의 금융상품 계약을 조회할 수 있습니다."),
    FINANCIAL_PRODUCT_CHILD_ONLY(
            HttpStatus.FORBIDDEN, "자녀 회원만 본인의 금융상품 계약을 조회할 수 있습니다."),
    FINANCIAL_PRODUCT_ENROLLMENT_DUPLICATED(
            HttpStatus.CONFLICT, "동일 상품의 승인 대기 중인 가입 요청이 있습니다."),
    FINANCIAL_PRODUCT_ENROLLMENT_NOT_PENDING(
            HttpStatus.CONFLICT, "이미 처리된 금융상품 가입 요청입니다."),
    FINANCIAL_PRODUCT_INVALID_TERM(
            HttpStatus.BAD_REQUEST, "상품에서 지원하지 않는 가입 기간입니다."),
    FINANCIAL_PRODUCT_INVALID_AMOUNT(
            HttpStatus.BAD_REQUEST, "상품의 가입 가능 금액 범위를 벗어났습니다."),
    FINANCIAL_PRODUCT_INSUFFICIENT_GRADE(
            HttpStatus.BAD_REQUEST, "월간 적용 등급이 대출상품의 요구등급보다 낮습니다."),
    FINANCIAL_PRODUCT_DEPOSIT_INSUFFICIENT_GRADE(
            HttpStatus.BAD_REQUEST, "월간 적용 등급이 예금상품의 요구등급보다 낮습니다."),
    FINANCIAL_PRODUCT_SAVING_INSUFFICIENT_GRADE(
            HttpStatus.BAD_REQUEST, "월간 적용 등급이 적금상품의 요구등급보다 낮습니다."),
    FINANCIAL_PRODUCT_LOAN_GRADE_RESTRICTED(
            HttpStatus.BAD_REQUEST, "현재 월간 적용 등급으로는 대출에 가입할 수 없습니다."),
    FINANCIAL_PRODUCT_PARENT_NOT_CONNECTED(
            HttpStatus.BAD_REQUEST, "연결된 부모 회원을 찾을 수 없습니다."),
    FINANCIAL_PRODUCT_PENDING_TRANSFER_NOT_FOUND(
            HttpStatus.CONFLICT, "가입 요청에 연결된 대기 송금을 찾을 수 없습니다."),
    FINANCIAL_PRODUCT_CUSTOM_INVALID_CONDITION(
            HttpStatus.BAD_REQUEST, "부모 생성 금융상품의 입력 조건이 올바르지 않습니다."),
    FINANCIAL_PRODUCT_CUSTOM_HAS_ENROLLMENTS(
            HttpStatus.CONFLICT, "가입 또는 승인 대기 중인 자녀가 있어 상품을 삭제할 수 없습니다."),
    FINANCIAL_PRODUCT_SAVING_NOT_FREE(
            HttpStatus.BAD_REQUEST, "자유적금만 직접 납입할 수 있습니다."),
    FINANCIAL_PRODUCT_ENROLLMENT_NOT_ACTIVE(
            HttpStatus.CONFLICT, "진행 중인 금융상품 가입이 아닙니다."),
    FINANCIAL_PRODUCT_COMPLETION_DETAIL_NOT_AVAILABLE(
            HttpStatus.CONFLICT, "만기된 예·적금 또는 완납된 대출만 상세 이력을 조회할 수 있습니다."),
    FINANCIAL_PRODUCT_TERMINATION_NOT_AVAILABLE(
            HttpStatus.CONFLICT, "만기 전 진행 중인 예금·적금만 중도해지할 수 있습니다."),
    FINANCIAL_PRODUCT_SAVING_MONTHLY_LIMIT_EXCEEDED(
            HttpStatus.BAD_REQUEST, "자유적금의 월 최대 납입 한도를 초과했습니다."),
    FINANCIAL_PRODUCT_LOAN_EARLY_REPAYMENT_NOT_AVAILABLE(
            HttpStatus.CONFLICT, "상환 중인 대출만 조기상환할 수 있습니다."),
    FINANCIAL_PRODUCT_LOAN_EARLY_REPAYMENT_AMOUNT_EXCEEDED(
            HttpStatus.BAD_REQUEST, "조기상환 금액이 남은 상환 금액을 초과했습니다."),
    FINLIFE_API_KEY_MISSING(
            HttpStatus.SERVICE_UNAVAILABLE, "금감원 API 설정이 필요합니다."),
    FINLIFE_API_UNAVAILABLE(
            HttpStatus.BAD_GATEWAY, "금감원 금융상품 정보를 불러올 수 없습니다."),
    FINLIFE_RESPONSE_INVALID(
            HttpStatus.BAD_GATEWAY, "금감원 금융상품 응답이 올바르지 않습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
