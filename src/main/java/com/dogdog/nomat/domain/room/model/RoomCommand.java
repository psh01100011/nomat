package com.dogdog.nomat.domain.room.model;

import java.time.LocalDateTime;

public record RoomCommand(
        RoomCommandType type,
        Long userId,
        RoomMember member,
        Long targetUserId,
        String randomSeed,
        LocalDateTime requestedAt
) {

    public static RoomCommand join(RoomMember member, LocalDateTime requestedAt) {
        return new RoomCommand(RoomCommandType.JOIN, member.userId(), member, null, null, requestedAt);
    }

    public static RoomCommand leave(Long userId, LocalDateTime requestedAt) {
        return new RoomCommand(RoomCommandType.LEAVE, userId, null, null, null, requestedAt);
    }

    public static RoomCommand kick(Long hostUserId, Long targetUserId, LocalDateTime requestedAt) {
        return new RoomCommand(RoomCommandType.KICK, hostUserId, null, targetUserId, null, requestedAt);
    }

    public static RoomCommand start(Long hostUserId, String randomSeed, LocalDateTime requestedAt) {
        return new RoomCommand(RoomCommandType.START, hostUserId, null, null, randomSeed, requestedAt);
    }

    public static RoomCommand close(Long hostUserId, LocalDateTime requestedAt) {
        return new RoomCommand(RoomCommandType.CLOSE, hostUserId, null, null, null, requestedAt);
    }

    public static RoomCommand endGame(Long userId, LocalDateTime requestedAt) {
        return new RoomCommand(RoomCommandType.END_GAME, userId, null, null, null, requestedAt);
    }
}
