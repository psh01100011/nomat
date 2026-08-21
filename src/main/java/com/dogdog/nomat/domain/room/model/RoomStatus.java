package com.dogdog.nomat.domain.room.model;

public enum RoomStatus {
    WAITING,
    PLAYING,
    CLOSED,
    ENDED;

    public boolean isTerminal() {
        return this == CLOSED || this == ENDED;
    }
}
