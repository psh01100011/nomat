package com.dogdog.nomat.domain.room.dto;

import com.dogdog.nomat.domain.room.model.RoomAnswerHint;
import com.dogdog.nomat.domain.room.model.RoomGameQuestion;
import com.dogdog.nomat.domain.room.model.RoomGameQuestionOutcome;
import com.dogdog.nomat.domain.room.model.RoomGameState;
import com.dogdog.nomat.domain.room.model.RoomMember;
import com.dogdog.nomat.domain.room.model.RoomState;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record RoomGameSnapshotResponse(
        Long roomId,
        String status,
        CurrentQuestionResponse currentQuestion,
        boolean hintRevealed,
        String hintText,
        boolean questionEnded,
        Long currentQuestionWinnerUserId,
        String currentQuestionWinnerAnswer,
        int skipVoteCount,
        int skipVoteThreshold,
        boolean currentUserSkipVoted,
        List<ScoreResponse> scores,
        List<QuestionOutcomeResponse> questionOutcomes,
        LocalDateTime startedAt,
        LocalDateTime endedAt
) {

    public static RoomGameSnapshotResponse of(
            RoomState room,
            RoomGameState gameState,
            Long currentUserId,
            int skipVoteThreshold
    ) {
        RoomGameQuestion currentQuestion = gameState == null ? null : gameState.currentQuestion();
        boolean questionEnded = gameState != null && gameState.hasCurrentQuestionEnded();
        boolean hintRevealed = gameState != null && gameState.hintRevealed();

        return new RoomGameSnapshotResponse(
                room.roomId(),
                room.status().name(),
                CurrentQuestionResponse.of(currentQuestion, gameState, questionEnded),
                hintRevealed,
                hintRevealed && currentQuestion != null ? RoomAnswerHint.from(currentQuestion.primaryAnswer()) : null,
                questionEnded,
                gameState == null ? null : gameState.currentQuestionWinnerUserId(),
                gameState == null ? null : gameState.currentQuestionWinnerAnswer(),
                gameState == null ? 0 : gameState.skipVoteCount(),
                skipVoteThreshold,
                gameState != null && gameState.hasSkipVote(currentUserId),
                scoreResponses(room, gameState),
                questionOutcomeResponses(gameState),
                gameState == null ? null : gameState.startedAt(),
                gameState == null ? null : gameState.endedAt()
        );
    }

    private static List<ScoreResponse> scoreResponses(RoomState room, RoomGameState gameState) {
        return room.members().stream()
                .map(member -> ScoreResponse.of(member, gameState))
                .toList();
    }

    private static List<QuestionOutcomeResponse> questionOutcomeResponses(RoomGameState gameState) {
        if (gameState == null) {
            return List.of();
        }

        return gameState.questionOutcomes()
                .values()
                .stream()
                .sorted(Comparator.comparing(RoomGameQuestionOutcome::questionNumber))
                .map(QuestionOutcomeResponse::from)
                .toList();
    }

    public record CurrentQuestionResponse(
            Long questionId,
            int questionNumber,
            String promptText,
            String mediaUrl,
            String mediaSourceType,
            Integer mediaStartTimeMs,
            Integer mediaEndTimeMs,
            Integer mediaDurationMs,
            LocalDateTime startedAt,
            LocalDateTime endsAt,
            Integer durationSeconds,
            String answerText
    ) {

        private static CurrentQuestionResponse of(
                RoomGameQuestion question,
                RoomGameState gameState,
                boolean questionEnded
        ) {
            if (question == null || gameState == null) {
                return null;
            }

            return new CurrentQuestionResponse(
                    question.questionId(),
                    question.questionNumber(),
                    question.promptText(),
                    question.mediaUrl(),
                    question.mediaSourceType(),
                    question.mediaStartTimeMs(),
                    question.mediaEndTimeMs(),
                    question.mediaDurationMs(),
                    gameState.currentQuestionStartedAt(),
                    gameState.currentQuestionEndsAt(),
                    gameState.currentQuestionDurationSeconds(),
                    questionEnded ? question.primaryAnswer() : null
            );
        }
    }

    public record ScoreResponse(
            Long userId,
            String nickname,
            String profileImageUrl,
            String userType,
            int score
    ) {

        private static ScoreResponse of(RoomMember member, RoomGameState gameState) {
            return new ScoreResponse(
                    member.userId(),
                    member.nickname(),
                    member.profileImageUrl(),
                    member.userType().name(),
                    gameState == null ? 0 : gameState.scores().getOrDefault(member.userId(), 0)
            );
        }
    }

    public record QuestionOutcomeResponse(
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

        private static QuestionOutcomeResponse from(RoomGameQuestionOutcome outcome) {
            return new QuestionOutcomeResponse(
                    outcome.questionId(),
                    outcome.questionNumber(),
                    outcome.winnerUserId(),
                    outcome.winnerAnswer(),
                    outcome.earnedScore(),
                    outcome.answeredMs(),
                    outcome.endedReason(),
                    outcome.startedAt(),
                    outcome.endedAt()
            );
        }
    }
}
