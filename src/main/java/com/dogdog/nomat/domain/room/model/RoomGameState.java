package com.dogdog.nomat.domain.room.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record RoomGameState(
        Long roomId,
        String randomSeed,
        List<RoomGameQuestion> questions,
        int currentQuestionIndex,
        LocalDateTime currentQuestionStartedAt,
        Long currentQuestionWinnerUserId,
        String currentQuestionWinnerAnswer,
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
        return new RoomGameState(roomId, randomSeed, questions, -1, null, null, null, scores, startedAt, null);
    }

    public boolean hasCurrentQuestion() {
        return currentQuestionIndex >= 0 && currentQuestionIndex < questions.size();
    }

    public RoomGameQuestion currentQuestion() {
        if (!hasCurrentQuestion()) {
            return null;
        }

        return questions.get(currentQuestionIndex);
    }

    public boolean hasCurrentQuestionWinner() {
        return currentQuestionWinnerUserId != null;
    }

    public RoomGameState withCorrectAnswer(Long userId, String answer, int scoreToAdd) {
        Map<Long, Integer> nextScores = new java.util.HashMap<>(scores);
        nextScores.merge(userId, scoreToAdd, Integer::sum);
        return new RoomGameState(
                roomId,
                randomSeed,
                questions,
                currentQuestionIndex,
                currentQuestionStartedAt,
                userId,
                answer,
                nextScores,
                startedAt,
                endedAt
        );
    }
}
