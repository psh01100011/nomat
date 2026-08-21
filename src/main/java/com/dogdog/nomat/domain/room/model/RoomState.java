package com.dogdog.nomat.domain.room.model;

import com.dogdog.nomat.domain.game.entity.TimeLimitMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record RoomState(
        Long roomId,
        String title,
        RoomStatus status,
        Long mapId,
        String mapTitle,
        String mapThumbnailUrl,
        int mapQuestionCount,
        int mapVersion,
        boolean hasPassword,
        int maxPlayers,
        int selectedQuestionCount,
        int answerTimeLimitSeconds,
        TimeLimitMode timeLimitMode,
        boolean audioRepeatEnabled,
        boolean initialHintEnabled,
        int initialHintTriggerSeconds,
        Long hostUserId,
        List<RoomMember> members,
        Set<Long> kickedUserIds,
        String randomSeed,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime endedAt
) {

    public RoomState {
        members = members == null ? List.of() : List.copyOf(members);
        kickedUserIds = kickedUserIds == null ? Set.of() : Set.copyOf(kickedUserIds);
    }

    public static RoomState waiting(
            Long roomId,
            String title,
            Long mapId,
            String mapTitle,
            String mapThumbnailUrl,
            int mapQuestionCount,
            int mapVersion,
            boolean hasPassword,
            int maxPlayers,
            int selectedQuestionCount,
            int answerTimeLimitSeconds,
            TimeLimitMode timeLimitMode,
            boolean audioRepeatEnabled,
            boolean initialHintEnabled,
            int initialHintTriggerSeconds,
            RoomMember host,
            LocalDateTime createdAt
    ) {
        RoomMember hostMember = host.asHost();
        return new RoomState(
                roomId,
                title,
                RoomStatus.WAITING,
                mapId,
                mapTitle,
                mapThumbnailUrl,
                mapQuestionCount,
                mapVersion,
                hasPassword,
                maxPlayers,
                selectedQuestionCount,
                answerTimeLimitSeconds,
                timeLimitMode,
                audioRepeatEnabled,
                initialHintEnabled,
                initialHintTriggerSeconds,
                hostMember.userId(),
                List.of(hostMember),
                Set.of(),
                null,
                createdAt,
                null,
                null
        );
    }

    public int memberCount() {
        return members.size();
    }

    public boolean hasMember(Long userId) {
        return members.stream().anyMatch(member -> Objects.equals(member.userId(), userId));
    }

    public boolean isHost(Long userId) {
        return Objects.equals(hostUserId, userId);
    }

    public boolean isFull() {
        return members.size() >= maxPlayers;
    }

    public boolean isKicked(Long userId) {
        return kickedUserIds.contains(userId);
    }

    public Optional<RoomMember> findMember(Long userId) {
        return members.stream()
                .filter(member -> Objects.equals(member.userId(), userId))
                .findFirst();
    }

    public Optional<RoomMember> nextHostAfter(Long leavingUserId) {
        return members.stream()
                .filter(member -> !Objects.equals(member.userId(), leavingUserId))
                .min(Comparator.comparing(RoomMember::joinedAt));
    }

    public RoomState withJoinedMember(RoomMember member) {
        List<RoomMember> nextMembers = new ArrayList<>(members);
        nextMembers.add(member.asMember());
        return withMembers(nextMembers, hostUserId);
    }

    public RoomState withoutMember(Long userId, Long nextHostUserId) {
        List<RoomMember> nextMembers = members.stream()
                .filter(member -> !Objects.equals(member.userId(), userId))
                .map(member -> Objects.equals(member.userId(), nextHostUserId) ? member.asHost() : member.asMember())
                .toList();

        return withMembers(nextMembers, nextHostUserId);
    }

    public RoomState withKickedMember(Long userId) {
        List<RoomMember> nextMembers = members.stream()
                .filter(member -> !Objects.equals(member.userId(), userId))
                .toList();
        Set<Long> nextKickedUserIds = new java.util.HashSet<>(kickedUserIds);
        nextKickedUserIds.add(userId);

        return new RoomState(
                roomId,
                title,
                status,
                mapId,
                mapTitle,
                mapThumbnailUrl,
                mapQuestionCount,
                mapVersion,
                hasPassword,
                maxPlayers,
                selectedQuestionCount,
                answerTimeLimitSeconds,
                timeLimitMode,
                audioRepeatEnabled,
                initialHintEnabled,
                initialHintTriggerSeconds,
                hostUserId,
                nextMembers,
                nextKickedUserIds,
                randomSeed,
                createdAt,
                startedAt,
                endedAt
        );
    }

    public RoomState started(String randomSeed, LocalDateTime startedAt) {
        return withStatus(RoomStatus.PLAYING, randomSeed, startedAt, null);
    }

    public RoomState closed(LocalDateTime closedAt) {
        return withStatus(RoomStatus.CLOSED, randomSeed, startedAt, closedAt);
    }

    public RoomState ended(LocalDateTime endedAt) {
        return withStatus(RoomStatus.ENDED, randomSeed, startedAt, endedAt);
    }

    private RoomState withMembers(List<RoomMember> nextMembers, Long nextHostUserId) {
        return new RoomState(
                roomId,
                title,
                status,
                mapId,
                mapTitle,
                mapThumbnailUrl,
                mapQuestionCount,
                mapVersion,
                hasPassword,
                maxPlayers,
                selectedQuestionCount,
                answerTimeLimitSeconds,
                timeLimitMode,
                audioRepeatEnabled,
                initialHintEnabled,
                initialHintTriggerSeconds,
                nextHostUserId,
                nextMembers,
                kickedUserIds,
                randomSeed,
                createdAt,
                startedAt,
                endedAt
        );
    }

    private RoomState withStatus(
            RoomStatus nextStatus,
            String nextRandomSeed,
            LocalDateTime nextStartedAt,
            LocalDateTime nextEndedAt
    ) {
        return new RoomState(
                roomId,
                title,
                nextStatus,
                mapId,
                mapTitle,
                mapThumbnailUrl,
                mapQuestionCount,
                mapVersion,
                hasPassword,
                maxPlayers,
                selectedQuestionCount,
                answerTimeLimitSeconds,
                timeLimitMode,
                audioRepeatEnabled,
                initialHintEnabled,
                initialHintTriggerSeconds,
                hostUserId,
                members,
                kickedUserIds,
                nextRandomSeed,
                createdAt,
                nextStartedAt,
                nextEndedAt
        );
    }
}
