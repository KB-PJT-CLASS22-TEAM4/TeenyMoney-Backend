package com.teenyfin.teenymoney.domain.quest.mapper;

import com.teenyfin.teenymoney.domain.quest.vo.DeclineReasonCode;
import com.teenyfin.teenymoney.domain.quest.vo.QuestVO;
import com.teenyfin.teenymoney.domain.quest.vo.QuestStatus;
import com.teenyfin.teenymoney.domain.quest.vo.QuestVerificationVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
}
