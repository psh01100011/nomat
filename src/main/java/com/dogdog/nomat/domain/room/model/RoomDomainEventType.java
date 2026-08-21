package com.dogdog.nomat.domain.room.model;

public enum RoomDomainEventType {
    MEMBER_JOINED,
    MEMBER_LEFT,
    HOST_CHANGED,
    MEMBER_KICKED,
    ROOM_CLOSED,
    GAME_STARTED,
    CHAT_MESSAGE,
    CORRECT_ANSWER,
    SCORE_UPDATED,
    QUESTION_ENDED,
    GAME_ENDED
}
