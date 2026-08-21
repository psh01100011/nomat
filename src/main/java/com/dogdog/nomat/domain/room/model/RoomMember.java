package com.dogdog.nomat.domain.room.model;

import java.time.LocalDateTime;

public record RoomMember(
        Long userId,
        String nickname,
        String profileImageUrl,
        boolean host,
        LocalDateTime joinedAt
) {

    public RoomMember asHost() {
        return new RoomMember(userId, nickname, profileImageUrl, true, joinedAt);
    }

    public RoomMember asMember() {
        return new RoomMember(userId, nickname, profileImageUrl, false, joinedAt);
    }
}
