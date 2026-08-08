package com.teenyfin.teenymoney.domain.categoryPolicy.exception;

import com.teenyfin.teenymoney.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CategoryPolicyErrorCode implements ErrorCode {

    INVALID_ROLE(HttpStatus.BAD_REQUEST, "유효하지 않은 역할입니다."),
    CATEGORY_POLICY_NOT_FOUND(HttpStatus.BAD_REQUEST, "카테고리 정책을 찾을 수 없습니다"),
    CHILD_CAN_NOT_UPDATE_CATEGORY_POLICY(HttpStatus.FORBIDDEN, "자녀는 카테고리 정책에 대한 수정 권한이 없습니다."),
    CHILD_ID_REQUIRED(HttpStatus.BAD_REQUEST, "부모는 조회할 자녀의 아이디를 지정해야 합니다."),
    FORBIDDEN_TO_CHILD(HttpStatus.FORBIDDEN, "해당 자녀에 대한 권한이 없습니다."),
    INVALID_CATEGORY_POLICY_ID(HttpStatus.BAD_REQUEST, "일부 정책이 본인 소유가 아니거나 존재하지 않습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
