package com.dogdog.nomat.domain.room.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record RoomGameState(
        Long roomId,
        String randomSeed,
        List<RoomGameQuestion> questions,
        int currentQuestionIndex,
        Map<Long, Integer> scores,
        LocalDateTime startedAt,
        LocalDateTime endedAt
) {

    public RoomGameState {
        questions = questions == null ? List.of() : List.copyOf(questions);
        scores = scores == null ? Map.of() : Map.copyOf(scores);
    }

    public static RoomGameState started(
            Long roomId,
            String randomSeed,
            List<RoomGameQuestion> questions,
            Map<Long, Integer> scores,
            LocalDateTime startedAt
    ) {
        return new RoomGameState(roomId, randomSeed, questions, -1, scores, startedAt, null);
    }
}
