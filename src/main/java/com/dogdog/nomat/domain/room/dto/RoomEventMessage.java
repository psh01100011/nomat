package com.dogdog.nomat.domain.room.dto;

import com.dogdog.nomat.domain.room.model.RoomDomainEvent;
import java.time.LocalDateTime;

public record RoomEventMessage(
        String type,
        Long roomId,
        Long userId,
        Long targetUserId,
        Long previousHostUserId,
        Long newHostUserId,
        int memberCount,
        String closedReason,
        String endedReason,
        String content,
        String nickname,
        Integer score,
        Integer questionNumber,
        Long questionId,
        String promptText,
        String mediaUrl,
        String mediaSourceType,
        Integer mediaDurationMs,
        Integer durationSeconds,
        LocalDateTime startedAt,
        LocalDateTime endsAt,
        Boolean audioRepeatEnabled,
        Integer answerTimeLimitSeconds,
        Boolean initialHintEnabled,
        Integer initialHintTriggerSeconds,
        String hint,
        LocalDateTime occurredAt,
        Integer skipVoteCount,
        Integer skipVoteThreshold
) {

    public static RoomEventMessage from(RoomDomainEvent event) {
        return new RoomEventMessage(
                event.type().name(),
                event.roomId(),
                event.userId(),
                event.targetUserId(),
                event.previousHostUserId(),
                event.newHostUserId(),
                event.memberCount(),
                event.closedReason() == null ? null : event.closedReason().name(),
                event.endedReason() == null ? null : event.endedReason().name(),
                event.content(),
                event.nickname(),
                event.score(),
                event.questionNumber(),
                event.questionId(),
                event.promptText(),
                event.mediaUrl(),
                event.mediaSourceType(),
                event.mediaDurationMs(),
                event.durationSeconds(),
                event.startedAt(),
                event.endsAt(),
                event.audioRepeatEnabled(),
                event.answerTimeLimitSeconds(),
                event.initialHintEnabled(),
                event.initialHintTriggerSeconds(),
                event.hint(),
                event.occurredAt(),
                event.skipVoteCount(),
                event.skipVoteThreshold()
        );
    }
}
