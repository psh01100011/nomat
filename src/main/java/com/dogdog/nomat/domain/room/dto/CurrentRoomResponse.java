package com.dogdog.nomat.domain.room.dto;

import com.dogdog.nomat.domain.room.model.RoomState;

public record CurrentRoomResponse(
        Long roomId,
        String status
) {

    public static CurrentRoomResponse from(RoomState room) {
        return new CurrentRoomResponse(room.roomId(), room.status().name());
    }

    public static CurrentRoomResponse of(Long roomId, String status) {
        return new CurrentRoomResponse(roomId, status);
    }
}
