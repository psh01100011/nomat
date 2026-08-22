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
        Integer currentQuestionDurationSeconds,
        LocalDateTime currentQuestionEndsAt,
        LocalDateTime currentQuestionEndedAt,
        boolean hintRevealed,
        Long currentQuestionWinnerUserId,
        String currentQuestionWinnerAnswer,
        Map<Long, Integer> scores,
        Map<Integer, RoomGameQuestionOutcome> questionOutcomes,
        LocalDateTime startedAt,
        LocalDateTime endedAt
) {

    public RoomGameState {
        questions = questions == null ? List.of() : List.copyOf(questions);
        scores = scores == null ? Map.of() : Map.copyOf(scores);
        questionOutcomes = questionOutcomes == null ? Map.of() : Map.copyOf(questionOutcomes);
    }

    public static RoomGameState started(
            Long roomId,
            String randomSeed,
            List<RoomGameQuestion> questions,
            Map<Long, Integer> scores,
            LocalDateTime startedAt
    ) {
        return new RoomGameState(
                roomId,
                randomSeed,
                questions,
                -1,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                scores,
                Map.of(),
                startedAt,
                null
        );
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

    public boolean hasCurrentQuestionEnded() {
        return currentQuestionEndedAt != null;
    }

    public RoomGameState withStartedQuestion(
            int nextQuestionIndex,
            LocalDateTime startedAt,
            int durationSeconds
    ) {
        return new RoomGameState(
                roomId,
                randomSeed,
                questions,
                nextQuestionIndex,
                startedAt,
                durationSeconds,
                startedAt.plusSeconds(durationSeconds),
                null,
                false,
                null,
                null,
                scores,
                questionOutcomes,
                this.startedAt,
                endedAt
        );
    }

    public RoomGameState withHintRevealed() {
        return new RoomGameState(
                roomId,
                randomSeed,
                questions,
                currentQuestionIndex,
                currentQuestionStartedAt,
                currentQuestionDurationSeconds,
                currentQuestionEndsAt,
                currentQuestionEndedAt,
                true,
                currentQuestionWinnerUserId,
                currentQuestionWinnerAnswer,
                scores,
                questionOutcomes,
                startedAt,
                endedAt
        );
    }

    public RoomGameState withQuestionEnded(LocalDateTime endedAt) {
        RoomGameQuestion question = currentQuestion();
        Map<Integer, RoomGameQuestionOutcome> nextOutcomes = new java.util.HashMap<>(questionOutcomes);
        if (question != null) {
            nextOutcomes.put(question.questionNumber(), new RoomGameQuestionOutcome(
                    question.questionId(),
                    question.questionNumber(),
                    currentQuestionWinnerUserId,
                    currentQuestionWinnerAnswer,
                    0,
                    null,
                    "TIME_OVER",
                    currentQuestionStartedAt,
                    endedAt
            ));
        }

        return new RoomGameState(
                roomId,
                randomSeed,
                questions,
                currentQuestionIndex,
                currentQuestionStartedAt,
                currentQuestionDurationSeconds,
                currentQuestionEndsAt,
                endedAt,
                hintRevealed,
                currentQuestionWinnerUserId,
                currentQuestionWinnerAnswer,
                scores,
                nextOutcomes,
                startedAt,
                this.endedAt
        );
    }

    public RoomGameState withCorrectAnswer(Long userId, String answer, int scoreToAdd) {
        Map<Long, Integer> nextScores = new java.util.HashMap<>(scores);
        nextScores.merge(userId, scoreToAdd, Integer::sum);
        LocalDateTime endedAt = LocalDateTime.now();
        RoomGameQuestion question = currentQuestion();
        Map<Integer, RoomGameQuestionOutcome> nextOutcomes = new java.util.HashMap<>(questionOutcomes);
        if (question != null) {
            nextOutcomes.put(question.questionNumber(), new RoomGameQuestionOutcome(
                    question.questionId(),
                    question.questionNumber(),
                    userId,
                    answer,
                    scoreToAdd,
                    answeredMs(endedAt),
                    "CORRECT_ANSWER",
                    currentQuestionStartedAt,
                    endedAt
            ));
        }

        return new RoomGameState(
                roomId,
                randomSeed,
                questions,
                currentQuestionIndex,
                currentQuestionStartedAt,
                currentQuestionDurationSeconds,
                currentQuestionEndsAt,
                endedAt,
                hintRevealed,
                userId,
                answer,
                nextScores,
                nextOutcomes,
                startedAt,
                this.endedAt
        );
    }

    private Integer answeredMs(LocalDateTime endedAt) {
        if (currentQuestionStartedAt == null || endedAt == null) {
            return null;
        }

        return Math.toIntExact(java.time.Duration.between(currentQuestionStartedAt, endedAt).toMillis());
    }
}
