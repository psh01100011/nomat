package com.dogdog.nomat.domain.room.model;

import java.time.LocalDateTime;

public record RoomGameQuestionOutcome(
        Long questionId,
        int questionNumber,
        Long winnerUserId,
        String winnerAnswer,
        int earnedScore,
        Integer answeredMs,
        String endedReason,
        LocalDateTime startedAt,
        LocalDateTime endedAt
) {
}
