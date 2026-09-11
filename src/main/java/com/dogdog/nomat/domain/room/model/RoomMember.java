package com.dogdog.nomat.domain.room.model;

import com.dogdog.nomat.domain.auth.model.AuthenticatedUserType;
import java.time.LocalDateTime;

public record RoomMember(
        Long userId,
        String nickname,
        String profileImageUrl,
        AuthenticatedUserType userType,
        boolean host,
        LocalDateTime joinedAt
) {

    public RoomMember {
        userType = userType == null ? AuthenticatedUserType.MEMBER : userType;
    }

    public RoomMember(
            Long userId,
            String nickname,
            String profileImageUrl,
            boolean host,
            LocalDateTime joinedAt
    ) {
        this(userId, nickname, profileImageUrl, AuthenticatedUserType.MEMBER, host, joinedAt);
    }

    public RoomMember asHost() {
        return new RoomMember(userId, nickname, profileImageUrl, userType, true, joinedAt);
    }

    public RoomMember asMember() {
        return new RoomMember(userId, nickname, profileImageUrl, userType, false, joinedAt);
    }

    public boolean isGuest() {
        return userType == AuthenticatedUserType.GUEST;
    }
}
