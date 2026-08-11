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
