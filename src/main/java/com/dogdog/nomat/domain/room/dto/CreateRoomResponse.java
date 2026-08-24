package com.dogdog.nomat.domain.room.dto;

import com.dogdog.nomat.domain.room.model.RoomState;

public record CreateRoomResponse(
        Long roomId,
        String status,
        Long hostUserId
) {

    public static CreateRoomResponse from(RoomState room) {
        return new CreateRoomResponse(
                room.roomId(),
                room.status().name(),
                room.hostUserId()
        );
    }
}
