package com.dogdog.nomat.domain.room.model;

import java.time.Instant;

public record RoomGameTimer(
        Long roomId,
        int questionIndex,
        RoomGameTimerType type,
        Instant dueAt
) {

    public String memberKey() {
        return roomId + ":" + questionIndex + ":" + type.name();
    }
}
