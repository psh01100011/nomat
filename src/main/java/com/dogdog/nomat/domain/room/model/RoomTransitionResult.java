package com.dogdog.nomat.domain.room.model;

import java.util.List;

public record RoomTransitionResult(
        RoomState room,
        List<RoomDomainEvent> events
) {

    public RoomTransitionResult {
        events = events == null ? List.of() : List.copyOf(events);
    }

    public static RoomTransitionResult of(RoomState room, RoomDomainEvent event) {
        return new RoomTransitionResult(room, List.of(event));
    }

    public static RoomTransitionResult of(RoomState room, List<RoomDomainEvent> events) {
        return new RoomTransitionResult(room, events);
    }
}
