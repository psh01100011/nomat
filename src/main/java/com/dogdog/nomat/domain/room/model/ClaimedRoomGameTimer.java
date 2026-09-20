package com.dogdog.nomat.domain.room.model;

public record ClaimedRoomGameTimer(
        RoomGameTimer timer,
        String ownerToken
) {
}
