package com.teenyfin.teenymoney.domain.teenyscore.dto.request;

import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreEventCode;
import lombok.Getter;

import java.util.Objects;

/**
 * 정책 계산이 끝난 점수 변경 정보를 공통 변경 로직에 전달한다.
 * eventKey는 같은 금융 이벤트의 중복 반영을 막는 식별자다.
 */
@Getter
public class TeenyScoreChangeRequestDTO {

    private final Long childId;
    private final TeenyScoreEventCode eventCode;
    private final Integer amount;
    private final String eventKey;
    private final String description;
    private final String referenceType;
    private final Long referenceId;

    public TeenyScoreChangeRequestDTO(
            Long childId,
            TeenyScoreEventCode eventCode,
            Integer amount,
            String eventKey,
            String description,
            String referenceType,
            Long referenceId) {
        this.childId = Objects.requireNonNull(childId);
        this.eventCode = Objects.requireNonNull(eventCode);
        this.amount = Objects.requireNonNull(amount);
        this.eventKey = requireText(eventKey, "eventKey");
        this.description = requireText(description, "description");
        this.referenceType = requireText(referenceType, "referenceType");
        this.referenceId = Objects.requireNonNull(referenceId);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "는 비어 있을 수 없습니다.");
        }
        return value;
    }
}
