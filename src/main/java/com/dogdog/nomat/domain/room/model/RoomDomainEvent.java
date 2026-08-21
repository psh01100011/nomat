package com.dogdog.nomat.domain.room.model;

import java.time.LocalDateTime;

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
        String nickname,
        Integer score,
        Integer questionNumber,
        Long questionId,
        String promptText,
        String mediaUrl,
        String mediaSourceType,
        String hint,
        LocalDateTime occurredAt
) {

    public static RoomDomainEvent memberJoined(RoomState room, Long userId, LocalDateTime occurredAt) {
        return new RoomDomainEvent(
                RoomDomainEventType.MEMBER_JOINED,
                room.roomId(),
                userId,
                null,
                null,
                null,
                room.memberCount(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                occurredAt
        );
    }

    public static RoomDomainEvent memberLeft(RoomState room, Long userId, LocalDateTime occurredAt) {
        return new RoomDomainEvent(
                RoomDomainEventType.MEMBER_LEFT,
                room.roomId(),
                userId,
                null,
                null,
                null,
                room.memberCount(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                occurredAt
        );
    }

    public static RoomDomainEvent hostChanged(
            Long roomId,
            Long previousHostUserId,
            Long newHostUserId,
            LocalDateTime occurredAt
    ) {
        return new RoomDomainEvent(
                RoomDomainEventType.HOST_CHANGED,
                roomId,
                null,
                null,
                previousHostUserId,
                newHostUserId,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                occurredAt
        );
    }

    public static RoomDomainEvent memberKicked(RoomState room, Long targetUserId, LocalDateTime occurredAt) {
        return new RoomDomainEvent(
                RoomDomainEventType.MEMBER_KICKED,
                room.roomId(),
                null,
                targetUserId,
                null,
                null,
                room.memberCount(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                occurredAt
        );
    }

    public static RoomDomainEvent roomClosed(
            Long roomId,
            RoomClosedReason reason,
            LocalDateTime occurredAt
    ) {
        return new RoomDomainEvent(
                RoomDomainEventType.ROOM_CLOSED,
                roomId,
                null,
                null,
                null,
                null,
                0,
                reason,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                occurredAt
        );
    }

    public static RoomDomainEvent gameStarted(RoomState room, LocalDateTime occurredAt) {
        return new RoomDomainEvent(
                RoomDomainEventType.GAME_STARTED,
                room.roomId(),
                null,
                null,
                null,
                null,
                room.memberCount(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                occurredAt
        );
    }

    public static RoomDomainEvent questionStarted(Long roomId, RoomGameQuestion question, LocalDateTime occurredAt) {
        return new RoomDomainEvent(
                RoomDomainEventType.QUESTION_STARTED,
                roomId,
                null,
                null,
                null,
                null,
                0,
                null,
                null,
                null,
                null,
                null,
                question.questionNumber(),
                question.questionId(),
                question.promptText(),
                question.mediaUrl(),
                question.mediaSourceType(),
                null,
                occurredAt
        );
    }

    public static RoomDomainEvent hintRevealed(
            Long roomId,
            Integer questionNumber,
            String hint,
            LocalDateTime occurredAt
    ) {
        return new RoomDomainEvent(
                RoomDomainEventType.HINT_REVEALED,
                roomId,
                null,
                null,
                null,
                null,
                0,
                null,
                null,
                null,
                null,
                null,
                questionNumber,
                null,
                null,
                null,
                null,
                hint,
                occurredAt
        );
    }

    public static RoomDomainEvent chatMessage(
            Long roomId,
            Long userId,
            String nickname,
            String content,
            LocalDateTime occurredAt
    ) {
        return new RoomDomainEvent(
                RoomDomainEventType.CHAT_MESSAGE,
                roomId,
                userId,
                null,
                null,
                null,
                0,
                null,
                null,
                content,
                nickname,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                occurredAt
        );
    }

    public static RoomDomainEvent correctAnswer(
            Long roomId,
            Long userId,
            String nickname,
            Integer questionNumber,
            LocalDateTime occurredAt
    ) {
        return new RoomDomainEvent(
                RoomDomainEventType.CORRECT_ANSWER,
                roomId,
                userId,
                null,
                null,
                null,
                0,
                null,
                null,
                null,
                nickname,
                null,
                questionNumber,
                null,
                null,
                null,
                null,
                null,
                occurredAt
        );
    }

    public static RoomDomainEvent scoreUpdated(
            Long roomId,
            Long userId,
            String nickname,
            int score,
            LocalDateTime occurredAt
    ) {
        return new RoomDomainEvent(
                RoomDomainEventType.SCORE_UPDATED,
                roomId,
                userId,
                null,
                null,
                null,
                0,
                null,
                null,
                null,
                nickname,
                score,
                null,
                null,
                null,
                null,
                null,
                null,
                occurredAt
        );
    }

    public static RoomDomainEvent questionEnded(
            Long roomId,
            Integer questionNumber,
            LocalDateTime occurredAt
    ) {
        return new RoomDomainEvent(
                RoomDomainEventType.QUESTION_ENDED,
                roomId,
                null,
                null,
                null,
                null,
                0,
                null,
                null,
                null,
                null,
                null,
                questionNumber,
                null,
                null,
                null,
                null,
                null,
                occurredAt
        );
    }

    public static RoomDomainEvent gameEnded(
            RoomState room,
            RoomEndedReason reason,
            LocalDateTime occurredAt
    ) {
        return new RoomDomainEvent(
                RoomDomainEventType.GAME_ENDED,
                room.roomId(),
                null,
                null,
                null,
                null,
                room.memberCount(),
                null,
                reason,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                occurredAt
        );
    }
}
