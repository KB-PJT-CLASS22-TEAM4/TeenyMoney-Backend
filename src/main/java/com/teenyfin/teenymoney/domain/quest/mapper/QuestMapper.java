package com.teenyfin.teenymoney.domain.quest.mapper;

import com.teenyfin.teenymoney.domain.quest.vo.DeclineReasonCode;
import com.teenyfin.teenymoney.domain.quest.vo.QuestVO;
import com.teenyfin.teenymoney.domain.quest.vo.QuestStatus;
import com.teenyfin.teenymoney.domain.quest.vo.QuestVerificationVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;
import java.time.LocalDateTime;

@Mapper
public interface QuestMapper {

    List<QuestVO> selectByCreationRequestKey(
            @Param("parentId") Long parentId,
            @Param("creationRequestKey") String creationRequestKey);

    QuestVO selectByIdForUpdateByChild(
            @Param("questId") Long questId,
            @Param("childId") Long childId);

    QuestVO selectByIdForUpdateByParent(
            @Param("questId") Long questId,
            @Param("parentId") Long parentId);

    int insert(QuestVO quest);

    int updateAvailable(QuestVO quest);

    int updateStatusByChild(
            @Param("questId") Long questId,
            @Param("childId") Long childId,
            @Param("fromStatus") QuestStatus fromStatus,
            @Param("toStatus") QuestStatus toStatus,
            @Param("updatedAt") LocalDateTime updatedAt);

    int updateDeclineByChild(
            @Param("questId") Long questId,
            @Param("childId") Long childId,
            @Param("reasonCode") DeclineReasonCode reasonCode,
            @Param("reasonDetail") String reasonDetail,
            @Param("endedAt") LocalDateTime endedAt);

    int deleteAvailable(
            @Param("questId") Long questId,
            @Param("parentId") Long parentId);

    List<QuestVO> selectPageByParent(
            @Param("memberId") Long memberId,
            @Param("childId") Long childId,
            @Param("statuses") List<QuestStatus> statuses,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("cursorId") Long cursorId,
            @Param("completed") boolean completed,
            @Param("limit") int limit);

    List<QuestVO> selectPageByChild(
            @Param("memberId") Long memberId,
            @Param("statuses") List<QuestStatus> statuses,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("cursorId") Long cursorId,
            @Param("completed") boolean completed,
            @Param("limit") int limit);

    QuestVO selectDetailByParent(
            @Param("questId") Long questId,
            @Param("memberId") Long memberId);

    QuestVO selectDetailByChild(
            @Param("questId") Long questId,
            @Param("memberId") Long memberId);

    QuestVerificationVO selectLatestVerification(@Param("questId") Long questId);

    QuestVerificationVO selectLatestVerificationForUpdate(
            @Param("questId") Long questId);

    int updateVerificationReview(
            @Param("verificationId") Long verificationId,
            @Param("questId") Long questId,
            @Param("status") String status,
            @Param("rejectionReason") String rejectionReason,
            @Param("reviewedAt") LocalDateTime reviewedAt);

    int updateCompletedByParent(
            @Param("questId") Long questId,
            @Param("parentId") Long parentId,
            @Param("endedAt") LocalDateTime endedAt,
            @Param("updatedAt") LocalDateTime updatedAt);

    int updateAfterRejectionByParent(
            @Param("questId") Long questId,
            @Param("parentId") Long parentId,
            @Param("toStatus") QuestStatus toStatus,
            @Param("remainingCount") Integer remainingCount,
            @Param("deadline") LocalDateTime deadline,
            @Param("endedAt") LocalDateTime endedAt,
            @Param("updatedAt") LocalDateTime updatedAt);

    int insertVerification(QuestVerificationVO verification);

    /**
     * 마감 대상을 상태별로 잠그고 가져온다. 다른 인스턴스가 잡은 행은 기다리지 않고 건너뛴다.
     *
     * excludeIds 는 이번 실행에서 이미 실패한 퀘스트다. 실패해도 상태가 그대로라
     * (deadline ASC, id ASC) 정렬에서 계속 맨 앞에 오기 때문에, 자바에서 건너뛰기만 하면
     * 조회 창이 앞으로 나가지 못하고 뒤의 정상 대상이 영원히 막힌다. 조회 단계에서 뺀다.
     * 비어 있으면 조건 자체가 붙지 않는다.
     */
    List<QuestVO> selectDeadlineTargetsForUpdate(
            @Param("status") QuestStatus status,
            @Param("now") LocalDateTime now,
            @Param("limit") int limit,
            @Param("excludeIds") Collection<Long> excludeIds);

    int updateStatusForDeadline(
            @Param("questId") Long questId,
            @Param("fromStatus") QuestStatus fromStatus,
            @Param("toStatus") QuestStatus toStatus,
            @Param("remainingCount") Integer remainingCount,
            @Param("endedAt") LocalDateTime endedAt);

    /**
     * 가족 연동이 해제될 때 그 부모-자녀의 진행 중인 퀘스트를 한 번에 마감한다.
     *
     * 마감 배치와 달리 티니점수를 건드리지 않는다. 자녀가 못 한 게 아니라 부모가 관계를 끊은 것이라
     * FAILED(-2점)가 아니라 EXPIRED 로 끝낸다.
     */
    int expireOpenQuestsByParentAndChild(
            @Param("parentId") Long parentId,
            @Param("childId") Long childId,
            @Param("endedAt") LocalDateTime endedAt);
}
