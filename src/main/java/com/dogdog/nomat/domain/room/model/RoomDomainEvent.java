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
                occurredAt
        );
    }
}
