package com.teenyfin.teenymoney.domain.quest.exception;

import com.teenyfin.teenymoney.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum QuestErrorCode implements ErrorCode {
    QUEST_CHILD_REQUIRED(HttpStatus.BAD_REQUEST,
            "퀘스트를 받을 자녀를 한 명 이상 선택해 주세요."),
    QUEST_CHILD_DUPLICATED(HttpStatus.BAD_REQUEST,
            "같은 자녀를 중복해서 선택할 수 없습니다."),
    QUEST_REWARD_INVALID(HttpStatus.BAD_REQUEST,
            "현금 보상은 0원 또는 100원 이상이어야 하며, 현금이나 티니점수 중 하나는 필요합니다."),
    QUEST_DEADLINE_INVALID(HttpStatus.BAD_REQUEST,
            "기한은 현재보다 미래이며 1년 이내여야 합니다."),
    QUEST_CREATION_KEY_INVALID(HttpStatus.BAD_REQUEST,
            "요청 식별 키를 확인해 주세요."),
    QUEST_PARENT_ONLY(HttpStatus.FORBIDDEN,
            "부모만 사용할 수 있는 기능입니다."),
    QUEST_CHILD_NOT_LINKED(HttpStatus.FORBIDDEN,
            "연결된 자녀의 퀘스트만 처리할 수 있습니다."),
    QUEST_NOT_FOUND_OR_ACCESS_DENIED(HttpStatus.NOT_FOUND,
            "퀘스트를 찾을 수 없거나 접근할 수 없습니다."),
    QUEST_STATUS_CONFLICT(HttpStatus.CONFLICT,
            "현재 퀘스트 상태에서는 처리할 수 없습니다. 새로고침해 주세요."),
    QUEST_DEADLINE_PASSED(HttpStatus.CONFLICT,
            "퀘스트 기한이 지났습니다. 새로고침해 주세요."),
    QUEST_CREATION_REQUEST_CONFLICT(HttpStatus.CONFLICT,
            "같은 요청 식별 키가 다른 퀘스트 내용에 사용되었습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
