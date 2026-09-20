package com.dogdog.nomat.domain.room.service;

import com.dogdog.nomat.domain.room.dto.RoomAudioLoadFailureRequest;
import com.dogdog.nomat.domain.room.dto.RoomSkipVoteRequest;
import com.dogdog.nomat.domain.room.model.RoomAnswerHint;
import com.dogdog.nomat.domain.room.model.RoomDomainEvent;
import com.dogdog.nomat.domain.room.model.RoomEndedReason;
import com.dogdog.nomat.domain.room.model.RoomGameQuestion;
import com.dogdog.nomat.domain.room.model.RoomGameState;
import com.dogdog.nomat.domain.room.model.RoomGameTimer;
import com.dogdog.nomat.domain.room.model.RoomGameTimerType;
import com.dogdog.nomat.domain.room.model.RoomMember;
import com.dogdog.nomat.domain.room.model.RoomState;
import com.dogdog.nomat.domain.room.model.RoomStatus;
import com.dogdog.nomat.domain.room.repository.RoomRedisRepository;
import com.dogdog.nomat.domain.room.repository.RoomGameTimerRepository;
import com.dogdog.nomat.global.exception.BusinessException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoomGameProgressService {

    private static final long NEXT_QUESTION_DELAY_SECONDS = 3;

    private final RoomRedisRepository roomRedisRepository;
    private final RoomGameTimerRepository roomGameTimerRepository;
    private final RoomEventPublisher roomEventPublisher;
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

    public void scheduleQuestionAdvance(Long roomId, int questionIndex) {
        scheduleTimer(new RoomGameTimer(
                roomId,
                questionIndex,
                RoomGameTimerType.NEXT_QUESTION,
                Instant.now().plus(Duration.ofSeconds(NEXT_QUESTION_DELAY_SECONDS))
        ));
        roomGameTimerRepository.removeQuestionTimers(roomId, questionIndex);
    }

    public void continueAfterQuestionEnded(Long roomId, int questionIndex) {
        RoomState room = roomRedisRepository.findById(roomId).orElse(null);
        RoomGameState gameState = roomRedisRepository.findGameState(roomId).orElse(null);
        if (room == null
                || gameState == null
                || room.status() != RoomStatus.PLAYING
                || gameState.currentQuestionIndex() != questionIndex
                || !gameState.hasCurrentQuestionEnded()) {
            return;
        }

        startQuestion(room, questionIndex + 1);
    }

    public void executeTimer(RoomGameTimer timer) {
        switch (timer.type()) {
            case HINT -> revealHint(timer.roomId(), timer.questionIndex());
            case QUESTION_TIMEOUT -> endQuestionByTimeout(timer.roomId(), timer.questionIndex());
            case NEXT_QUESTION -> continueAfterQuestionEnded(timer.roomId(), timer.questionIndex());
        }
    }

    public void voteToSkip(Long userId, Long roomId, RoomSkipVoteRequest request) {
        RoomState room = roomRedisRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "room_not_found"));
        RoomMember member = room.findMember(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.FORBIDDEN, "forbidden_room_access"));
        if (room.status() != RoomStatus.PLAYING) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "cannot_skip_question");
        }

        RoomGameState gameState = roomRedisRepository.findGameState(roomId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "room_not_found"));
        if (!gameState.hasCurrentQuestion() || gameState.hasCurrentQuestionEnded()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "cannot_skip_question");
        }

        RoomGameQuestion question = gameState.currentQuestion();
        if (!question.questionId().equals(request.questionId())
                || question.questionNumber() != request.questionNumber()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "stale_question");
        }

        int skipVoteThreshold = skipVoteThreshold(room.memberCount());
        LocalDateTime now = LocalDateTime.now();
        RoomGameState votedGameState = roomRedisRepository.tryVoteToSkip(
                        roomId,
                        gameState.currentQuestionIndex(),
                        question.questionId(),
                        userId,
                        skipVoteThreshold,
                        now
                )
                .orElse(null);
        if (votedGameState == null) {
            return;
        }

        RoomDomainEvent skipVoteUpdated = RoomDomainEvent.skipVoteUpdated(
                roomId,
                userId,
                member.userType().name(),
                question.questionNumber(),
                room.memberCount(),
                votedGameState.skipVoteCount(),
                skipVoteThreshold,
                now
        );

        if (votedGameState.hasCurrentQuestionEnded()) {
            roomEventPublisher.publish(List.of(
                    skipVoteUpdated,
                    RoomDomainEvent.questionEnded(roomId, question.questionNumber(), question.primaryAnswer(), now)
            ));
            scheduleQuestionAdvance(roomId, votedGameState.currentQuestionIndex());
            log.info(
                    "event=room_question_skipped roomId={} questionNumber={} voteCount={} threshold={}",
                    roomId, question.questionNumber(), votedGameState.skipVoteCount(), skipVoteThreshold
            );
            return;
        }

        roomEventPublisher.publish(List.of(skipVoteUpdated));
    }

    public void reportAudioLoadFailure(Long userId, Long roomId, RoomAudioLoadFailureRequest request) {
        RoomState room = roomRedisRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "room_not_found"));
        room.findMember(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.FORBIDDEN, "forbidden_room_access"));
        if (room.status() != RoomStatus.PLAYING || !"AUDIO".equals(room.questionType())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "cannot_report_audio_load_failure");
        }

        RoomGameState gameState = roomRedisRepository.findGameState(roomId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "room_not_found"));
        RoomGameQuestion question = gameState.currentQuestion();
        if (question == null
                || !question.questionId().equals(request.questionId())
                || question.questionNumber() != request.questionNumber()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "stale_question");
        }

        RoomGameState failedGameState = roomRedisRepository.tryEndQuestionForAudioLoadFailure(
                        roomId,
                        gameState.currentQuestionIndex(),
                        question.questionId()
                )
                .orElse(null);
        if (failedGameState == null) {
            return;
        }

        LocalDateTime now = failedGameState.currentQuestionEndedAt();
        roomEventPublisher.publish(List.of(RoomDomainEvent.questionAudioLoadFailed(
                roomId,
                question.questionId(),
                question.questionNumber(),
                now
        )));
        scheduleQuestionAdvance(roomId, failedGameState.currentQuestionIndex());
        log.warn(
                "event=room_question_audio_load_failed roomId={} questionId={} questionNumber={} reportedByUserId={}",
                roomId, question.questionId(), question.questionNumber(), userId
        );
    }

    public void revealHint(Long roomId, int questionIndex) {
        RoomGameState nextGameState = roomRedisRepository.tryRevealHint(roomId, questionIndex).orElse(null);
        if (nextGameState == null) {
            return;
        }

        RoomGameQuestion question = nextGameState.currentQuestion();
        roomEventPublisher.publish(List.of(RoomDomainEvent.hintRevealed(
                roomId,
                question.questionNumber(),
                RoomAnswerHint.from(question.primaryAnswer()),
                LocalDateTime.now()
        )));
        log.debug("event=room_question_hint_revealed roomId={} questionNumber={}", roomId, question.questionNumber());
    }

    public void endQuestionByTimeout(Long roomId, int questionIndex) {
        RoomGameState nextGameState = roomRedisRepository.tryEndQuestionByTimeout(roomId, questionIndex)
                .orElse(null);
        if (nextGameState == null) {
            return;
        }

        RoomGameQuestion question = nextGameState.currentQuestion();
        roomEventPublisher.publish(List.of(RoomDomainEvent.questionEnded(
                roomId,
                question.questionNumber(),
                question.primaryAnswer(),
                nextGameState.currentQuestionEndedAt()
        )));
        log.debug("event=room_question_timed_out roomId={} questionNumber={}", roomId, question.questionNumber());
        scheduleQuestionAdvance(roomId, questionIndex);
    }

    private void startQuestion(RoomState room, int questionIndex) {
        RoomGameState gameState = roomRedisRepository.findGameState(room.roomId()).orElse(null);
        if (gameState == null || questionIndex >= gameState.questions().size()) {
            if (gameState != null) {
                roomGameResultService.endGame(room.roomId(), null, RoomEndedReason.COMPLETED);
            }
            return;
        }

        RoomGameQuestion questionToStart = gameState.questions().get(questionIndex);
        int durationSeconds = actualDurationSeconds(room, questionToStart);
        LocalDateTime startedAt = LocalDateTime.now();
        RoomGameState nextGameState = roomRedisRepository.tryStartQuestion(
                        room.roomId(),
                        questionIndex,
                        startedAt,
                        durationSeconds
                )
                .orElse(null);
        if (nextGameState == null) {
            return;
        }
        RoomGameQuestion question = nextGameState.currentQuestion();
        roomEventPublisher.publish(List.of(RoomDomainEvent.questionStarted(
                room,
                question,
                durationSeconds,
                nextGameState.currentQuestionEndsAt(),
                startedAt
        )));
        log.debug(
                "event=room_question_started roomId={} questionId={} questionNumber={} durationSeconds={}",
                room.roomId(), question.questionId(), question.questionNumber(), durationSeconds
        );
        scheduleHint(room, questionIndex, durationSeconds);
        scheduleQuestionTimeout(room, questionIndex, durationSeconds);
    }

    private void scheduleHint(RoomState room, int questionIndex, int durationSeconds) {
        if (!room.initialHintEnabled()) {
            return;
        }

        long delaySeconds = Math.max(0, durationSeconds - room.initialHintTriggerSeconds());
        scheduleTimer(new RoomGameTimer(
                room.roomId(),
                questionIndex,
                RoomGameTimerType.HINT,
                Instant.now().plus(Duration.ofSeconds(delaySeconds))
        ));
    }

    private void scheduleQuestionTimeout(RoomState room, int questionIndex, int durationSeconds) {
        scheduleTimer(new RoomGameTimer(
                room.roomId(),
                questionIndex,
                RoomGameTimerType.QUESTION_TIMEOUT,
                Instant.now().plus(Duration.ofSeconds(durationSeconds))
        ));
    }

    private void scheduleTimer(RoomGameTimer timer) {
        roomGameTimerRepository.schedule(timer);
    }

    private int actualDurationSeconds(RoomState room, RoomGameQuestion question) {
        Integer mediaDurationMs = question.mediaDurationMs();
        if (mediaDurationMs == null) {
            return room.answerTimeLimitSeconds();
        }

        int mediaDurationSeconds = (int) Math.ceil(mediaDurationMs / 1000.0);
        return Math.max(room.answerTimeLimitSeconds(), mediaDurationSeconds);
    }

    private int skipVoteThreshold(int memberCount) {
        return memberCount / 2 + 1;
    }

}
