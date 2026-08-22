package com.dogdog.nomat.domain.room.service;

import com.dogdog.nomat.domain.room.model.RoomDomainEvent;
import com.dogdog.nomat.domain.room.model.RoomGameQuestion;
import com.dogdog.nomat.domain.room.model.RoomGameState;
import com.dogdog.nomat.domain.room.model.RoomState;
import com.dogdog.nomat.domain.room.model.RoomStatus;
import com.dogdog.nomat.domain.room.repository.RoomRedisRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoomGameProgressService {

    private final RoomRedisRepository roomRedisRepository;
    private final RoomEventPublisher roomEventPublisher;
    private final TaskScheduler taskScheduler;
    private final RoomGameResultService roomGameResultService;

    public void startFirstQuestion(RoomState room) {
        startQuestion(room, 0);
    }

    public void startNextQuestion(Long roomId) {
        RoomState room = roomRedisRepository.findById(roomId).orElse(null);
        RoomGameState gameState = roomRedisRepository.findGameState(roomId).orElse(null);
        if (room == null || gameState == null || room.status() != RoomStatus.PLAYING) {
            return;
        }

        startQuestion(room, gameState.currentQuestionIndex() + 1);
    }

    public void revealHint(Long roomId, int questionIndex) {
        RoomGameState gameState = roomRedisRepository.findGameState(roomId).orElse(null);
        if (gameState == null
                || gameState.currentQuestionIndex() != questionIndex
                || gameState.hintRevealed()
                || gameState.hasCurrentQuestionEnded()) {
            return;
        }

        RoomGameQuestion question = gameState.currentQuestion();
        RoomGameState nextGameState = gameState.withHintRevealed();
        roomRedisRepository.saveGameState(nextGameState);
        roomEventPublisher.publish(List.of(RoomDomainEvent.hintRevealed(
                roomId,
                question.questionNumber(),
                hint(question.primaryAnswer()),
                LocalDateTime.now()
        )));
    }

    public void endQuestionByTimeout(Long roomId, int questionIndex) {
        RoomGameState gameState = roomRedisRepository.findGameState(roomId).orElse(null);
        if (gameState == null
                || gameState.currentQuestionIndex() != questionIndex
                || gameState.hasCurrentQuestionEnded()
                || gameState.hasCurrentQuestionWinner()) {
            return;
        }

        RoomGameQuestion question = gameState.currentQuestion();
        RoomGameState nextGameState = gameState.withQuestionEnded(LocalDateTime.now());
        roomRedisRepository.saveGameState(nextGameState);
        roomEventPublisher.publish(List.of(RoomDomainEvent.questionEnded(
                roomId,
                question.questionNumber(),
                LocalDateTime.now()
        )));
    }

    private void startQuestion(RoomState room, int questionIndex) {
        RoomGameState gameState = roomRedisRepository.findGameState(room.roomId()).orElse(null);
        if (gameState == null || questionIndex >= gameState.questions().size()) {
            if (gameState != null) {
                roomGameResultService.endGame(room.roomId(), null, com.dogdog.nomat.domain.room.model.RoomEndedReason.COMPLETED);
            }
            return;
        }

        RoomGameQuestion questionToStart = gameState.questions().get(questionIndex);
        int durationSeconds = actualDurationSeconds(room, questionToStart);
        LocalDateTime startedAt = LocalDateTime.now();
        RoomGameState nextGameState = gameState.withStartedQuestion(questionIndex, startedAt, durationSeconds);
        RoomGameQuestion question = nextGameState.currentQuestion();
        roomRedisRepository.saveGameState(nextGameState);
        roomEventPublisher.publish(List.of(RoomDomainEvent.questionStarted(
                room,
                question,
                durationSeconds,
                nextGameState.currentQuestionEndsAt(),
                startedAt
        )));
        scheduleHint(room, questionIndex, durationSeconds);
        scheduleQuestionTimeout(room, questionIndex, durationSeconds);
    }

    private void scheduleHint(RoomState room, int questionIndex, int durationSeconds) {
        if (!room.initialHintEnabled()) {
            return;
        }

        long delaySeconds = Math.max(0, durationSeconds - room.initialHintTriggerSeconds());
        taskScheduler.schedule(
                () -> revealHint(room.roomId(), questionIndex),
                Instant.now().plus(Duration.ofSeconds(delaySeconds))
        );
    }

    private void scheduleQuestionTimeout(RoomState room, int questionIndex, int durationSeconds) {
        taskScheduler.schedule(
                () -> endQuestionByTimeout(room.roomId(), questionIndex),
                Instant.now().plus(Duration.ofSeconds(durationSeconds))
        );
    }

    private int actualDurationSeconds(RoomState room, RoomGameQuestion question) {
        Integer mediaDurationMs = question.mediaDurationMs();
        if (mediaDurationMs == null) {
            return room.answerTimeLimitSeconds();
        }

        int mediaDurationSeconds = (int) Math.ceil(mediaDurationMs / 1000.0);
        return Math.max(room.answerTimeLimitSeconds(), mediaDurationSeconds);
    }

    private String hint(String primaryAnswer) {
        if (primaryAnswer == null || primaryAnswer.isBlank()) {
            return null;
        }

        return primaryAnswer.substring(0, 1);
    }
}
