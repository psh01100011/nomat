package com.dogdog.nomat.domain.room.model;

import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record RoomDomainEvent(
        RoomDomainEventType type,
        Long roomId,
        Long userId,
        Long targetUserId,
        Long previousHostUserId,
        Long newHostUserId,
        int memberCount,
        RoomClosedReason closedReason,
        RoomEndedReason endedReason,
        String content,
        String clientMessageId,
        String nickname,
        Integer score,
        Integer questionNumber,
        Long questionId,
        String promptText,
        String mediaUrl,
        String mediaSourceType,
        Integer mediaDurationMs,
        Integer durationSeconds,
        LocalDateTime startedAt,
        LocalDateTime endsAt,
        Boolean audioRepeatEnabled,
        Integer answerTimeLimitSeconds,
        Boolean initialHintEnabled,
        Integer initialHintTriggerSeconds,
        String hint,
        LocalDateTime occurredAt,
        Integer skipVoteCount,
        Integer skipVoteThreshold
) {

    public static RoomDomainEvent memberJoined(RoomState room, Long userId, LocalDateTime occurredAt) {
        return base(RoomDomainEventType.MEMBER_JOINED, room.roomId(), userId, null, room.memberCount(), occurredAt);
    }

    public static RoomDomainEvent memberLeft(RoomState room, Long userId, LocalDateTime occurredAt) {
        return base(RoomDomainEventType.MEMBER_LEFT, room.roomId(), userId, null, room.memberCount(), occurredAt);
    }

    public static RoomDomainEvent hostChanged(
            Long roomId,
            Long previousHostUserId,
            Long newHostUserId,
            LocalDateTime occurredAt
    ) {
        return RoomDomainEvent.builder()
                .type(RoomDomainEventType.HOST_CHANGED)
                .roomId(roomId)
                .previousHostUserId(previousHostUserId)
                .newHostUserId(newHostUserId)
                .occurredAt(occurredAt)
                .build();
    }

    public static RoomDomainEvent memberKicked(RoomState room, Long targetUserId, LocalDateTime occurredAt) {
        return base(RoomDomainEventType.MEMBER_KICKED, room.roomId(), null, targetUserId, room.memberCount(), occurredAt);
    }

    public static RoomDomainEvent roomClosed(
            Long roomId,
            RoomClosedReason reason,
            LocalDateTime occurredAt
    ) {
        return RoomDomainEvent.builder()
                .type(RoomDomainEventType.ROOM_CLOSED)
                .roomId(roomId)
                .closedReason(reason)
                .occurredAt(occurredAt)
                .build();
    }

    public static RoomDomainEvent gameStarted(RoomState room, LocalDateTime occurredAt) {
        return base(RoomDomainEventType.GAME_STARTED, room.roomId(), null, null, room.memberCount(), occurredAt);
    }

    public static RoomDomainEvent questionStarted(
            RoomState room,
            RoomGameQuestion question,
            int durationSeconds,
            LocalDateTime endsAt,
            LocalDateTime occurredAt
    ) {
        return RoomDomainEvent.builder()
                .type(RoomDomainEventType.QUESTION_STARTED)
                .roomId(room.roomId())
                .questionNumber(question.questionNumber())
                .questionId(question.questionId())
                .promptText(question.promptText())
                .mediaUrl(question.mediaUrl())
                .mediaSourceType(question.mediaSourceType())
                .mediaDurationMs(question.mediaDurationMs())
                .durationSeconds(durationSeconds)
                .startedAt(occurredAt)
                .endsAt(endsAt)
                .audioRepeatEnabled(true)
                .answerTimeLimitSeconds(room.answerTimeLimitSeconds())
                .initialHintEnabled(room.initialHintEnabled())
                .initialHintTriggerSeconds(room.initialHintTriggerSeconds())
                .occurredAt(occurredAt)
                .build();
    }

    public static RoomDomainEvent hintRevealed(
            Long roomId,
            Integer questionNumber,
            String hint,
            LocalDateTime occurredAt
    ) {
        return RoomDomainEvent.builder()
                .type(RoomDomainEventType.HINT_REVEALED)
                .roomId(roomId)
                .questionNumber(questionNumber)
                .hint(hint)
                .occurredAt(occurredAt)
                .build();
    }

    public static RoomDomainEvent chatMessage(
            Long roomId,
            Long userId,
            String nickname,
            String content,
            String clientMessageId,
            LocalDateTime occurredAt
    ) {
        return RoomDomainEvent.builder()
                .type(RoomDomainEventType.CHAT_MESSAGE)
                .roomId(roomId)
                .userId(userId)
                .content(content)
                .clientMessageId(clientMessageId)
                .nickname(nickname)
                .occurredAt(occurredAt)
                .build();
    }

    public static RoomDomainEvent correctAnswer(
            Long roomId,
            Long userId,
            String nickname,
            Integer questionNumber,
            LocalDateTime occurredAt
    ) {
        return RoomDomainEvent.builder()
                .type(RoomDomainEventType.CORRECT_ANSWER)
                .roomId(roomId)
                .userId(userId)
                .nickname(nickname)
                .questionNumber(questionNumber)
                .occurredAt(occurredAt)
                .build();
    }

    public static RoomDomainEvent scoreUpdated(
            Long roomId,
            Long userId,
            String nickname,
            int score,
            LocalDateTime occurredAt
    ) {
        return RoomDomainEvent.builder()
                .type(RoomDomainEventType.SCORE_UPDATED)
                .roomId(roomId)
                .userId(userId)
                .nickname(nickname)
                .score(score)
                .occurredAt(occurredAt)
                .build();
    }

    public static RoomDomainEvent skipVoteUpdated(
            Long roomId,
            Long userId,
            Integer questionNumber,
            int memberCount,
            int skipVoteCount,
            int skipVoteThreshold,
            LocalDateTime occurredAt
    ) {
        return RoomDomainEvent.builder()
                .type(RoomDomainEventType.SKIP_VOTE_UPDATED)
                .roomId(roomId)
                .userId(userId)
                .memberCount(memberCount)
                .questionNumber(questionNumber)
                .skipVoteCount(skipVoteCount)
                .skipVoteThreshold(skipVoteThreshold)
                .occurredAt(occurredAt)
                .build();
    }

    public static RoomDomainEvent questionEnded(
            Long roomId,
            Integer questionNumber,
            LocalDateTime occurredAt
    ) {
        return RoomDomainEvent.builder()
                .type(RoomDomainEventType.QUESTION_ENDED)
                .roomId(roomId)
                .questionNumber(questionNumber)
                .occurredAt(occurredAt)
                .build();
    }

    public static RoomDomainEvent gameEnded(
            RoomState room,
            RoomEndedReason reason,
            LocalDateTime occurredAt
    ) {
        return RoomDomainEvent.builder()
                .type(RoomDomainEventType.GAME_ENDED)
                .roomId(room.roomId())
                .memberCount(room.memberCount())
                .endedReason(reason)
                .occurredAt(occurredAt)
                .build();
    }

    private static RoomDomainEvent base(
            RoomDomainEventType type,
            Long roomId,
            Long userId,
            Long targetUserId,
            int memberCount,
            LocalDateTime occurredAt
    ) {
        return RoomDomainEvent.builder()
                .type(type)
                .roomId(roomId)
                .userId(userId)
                .targetUserId(targetUserId)
                .memberCount(memberCount)
                .occurredAt(occurredAt)
                .build();
    }
}
