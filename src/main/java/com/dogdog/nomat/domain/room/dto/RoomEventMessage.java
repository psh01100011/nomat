package com.dogdog.nomat.domain.room.dto;

import com.dogdog.nomat.domain.room.model.RoomDomainEvent;
import java.time.LocalDateTime;

public record RoomEventMessage(
        String type,
        Long roomId,
        Long userId,
        Long targetUserId,
        Long previousHostUserId,
        Long newHostUserId,
        int memberCount,
        String closedReason,
        String endedReason,
        LocalDateTime occurredAt
) {

    public static RoomEventMessage from(RoomDomainEvent event) {
        return new RoomEventMessage(
                event.type().name(),
                event.roomId(),
                event.userId(),
                event.targetUserId(),
                event.previousHostUserId(),
                event.newHostUserId(),
                event.memberCount(),
                event.closedReason() == null ? null : event.closedReason().name(),
                event.endedReason() == null ? null : event.endedReason().name(),
                event.occurredAt()
        );
    }
}
