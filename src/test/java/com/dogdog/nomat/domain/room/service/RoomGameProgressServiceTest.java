package com.dogdog.nomat.domain.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.dogdog.nomat.domain.game.entity.TimeLimitMode;
import com.dogdog.nomat.domain.room.model.RoomDomainEventType;
import com.dogdog.nomat.domain.room.model.RoomGameQuestion;
import com.dogdog.nomat.domain.room.model.RoomGameState;
import com.dogdog.nomat.domain.room.model.RoomMember;
import com.dogdog.nomat.domain.room.model.RoomState;
import com.dogdog.nomat.domain.room.repository.RoomRedisRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

@ExtendWith(MockitoExtension.class)
class RoomGameProgressServiceTest {

    @Mock
    private RoomRedisRepository roomRedisRepository;

    @Mock
    private RoomEventPublisher roomEventPublisher;

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private RoomGameResultService roomGameResultService;

    @InjectMocks
    private RoomGameProgressService roomGameProgressService;

    @Test
    void startFirstQuestionSavesCurrentQuestionAndPublishesQuestionStarted() {
        RoomState room = room();
        given(roomRedisRepository.findGameState(25L)).willReturn(Optional.of(gameState()));

        roomGameProgressService.startFirstQuestion(room);

        ArgumentCaptor<RoomGameState> gameStateCaptor = ArgumentCaptor.forClass(RoomGameState.class);
        verify(roomRedisRepository).saveGameState(gameStateCaptor.capture());
        RoomGameState savedGameState = gameStateCaptor.getValue();
        assertThat(savedGameState.currentQuestionIndex()).isEqualTo(0);
        assertThat(savedGameState.currentQuestionStartedAt()).isNotNull();
        assertThat(savedGameState.currentQuestionDurationSeconds()).isEqualTo(30);
        assertThat(savedGameState.currentQuestionEndsAt()).isEqualTo(savedGameState.currentQuestionStartedAt().plusSeconds(30));
        assertThat(savedGameState.hintRevealed()).isFalse();

        verify(roomEventPublisher).publish(org.mockito.ArgumentMatchers.argThat(events ->
                events.size() == 1
                        && events.getFirst().type() == RoomDomainEventType.QUESTION_STARTED
                        && events.getFirst().questionNumber() == 1
                        && events.getFirst().questionId().equals(1L)
                        && events.getFirst().durationSeconds() == 30
                        && events.getFirst().endsAt() != null
                        && events.getFirst().audioRepeatEnabled()
                        && events.getFirst().answerTimeLimitSeconds() == 30
        ));
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void startFirstQuestionUsesAnswerTimeLimitForTextQuestion() {
        RoomState room = room();
        given(roomRedisRepository.findGameState(25L)).willReturn(Optional.of(gameState(textQuestion())));

        roomGameProgressService.startFirstQuestion(room);

        ArgumentCaptor<RoomGameState> gameStateCaptor = ArgumentCaptor.forClass(RoomGameState.class);
        verify(roomRedisRepository).saveGameState(gameStateCaptor.capture());
        assertThat(gameStateCaptor.getValue().currentQuestionDurationSeconds()).isEqualTo(30);
    }

    @Test
    void startFirstQuestionUsesAnswerTimeLimitWhenAudioIsShorterThanLimit() {
        RoomState room = room();
        given(roomRedisRepository.findGameState(25L)).willReturn(Optional.of(gameState(audioQuestion(12_000))));

        roomGameProgressService.startFirstQuestion(room);

        ArgumentCaptor<RoomGameState> gameStateCaptor = ArgumentCaptor.forClass(RoomGameState.class);
        verify(roomRedisRepository).saveGameState(gameStateCaptor.capture());
        assertThat(gameStateCaptor.getValue().currentQuestionDurationSeconds()).isEqualTo(30);
    }

    @Test
    void startFirstQuestionUsesAudioDurationWhenAudioIsLongerThanLimit() {
        RoomState room = room();
        given(roomRedisRepository.findGameState(25L)).willReturn(Optional.of(gameState(audioQuestion(45_000))));

        roomGameProgressService.startFirstQuestion(room);

        ArgumentCaptor<RoomGameState> gameStateCaptor = ArgumentCaptor.forClass(RoomGameState.class);
        verify(roomRedisRepository).saveGameState(gameStateCaptor.capture());
        RoomGameState savedGameState = gameStateCaptor.getValue();
        assertThat(savedGameState.currentQuestionDurationSeconds()).isEqualTo(45);
        assertThat(savedGameState.currentQuestionEndsAt()).isEqualTo(savedGameState.currentQuestionStartedAt().plusSeconds(45));
    }

    @Test
    void startFirstQuestionRoundsUpAudioDurationSeconds() {
        RoomState room = room();
        given(roomRedisRepository.findGameState(25L)).willReturn(Optional.of(gameState(audioQuestion(30_500))));

        roomGameProgressService.startFirstQuestion(room);

        ArgumentCaptor<RoomGameState> gameStateCaptor = ArgumentCaptor.forClass(RoomGameState.class);
        verify(roomRedisRepository).saveGameState(gameStateCaptor.capture());
        assertThat(gameStateCaptor.getValue().currentQuestionDurationSeconds()).isEqualTo(31);
    }

    @Test
    void revealHintSavesGameStateAndPublishesHint() {
        RoomGameState gameState = gameState().withStartedQuestion(0, now(), 30);
        given(roomRedisRepository.findGameState(25L)).willReturn(Optional.of(gameState));

        roomGameProgressService.revealHint(25L, 0);

        ArgumentCaptor<RoomGameState> gameStateCaptor = ArgumentCaptor.forClass(RoomGameState.class);
        verify(roomRedisRepository).saveGameState(gameStateCaptor.capture());
        assertThat(gameStateCaptor.getValue().hintRevealed()).isTrue();

        verify(roomEventPublisher).publish(org.mockito.ArgumentMatchers.argThat(events ->
                events.size() == 1
                        && events.getFirst().type() == RoomDomainEventType.HINT_REVEALED
                        && events.getFirst().hint().equals("정")
        ));
    }

    @Test
    void endQuestionByTimeoutSavesGameStateAndPublishesQuestionEnded() {
        RoomGameState gameState = gameState().withStartedQuestion(0, now(), 30);
        given(roomRedisRepository.findGameState(25L)).willReturn(Optional.of(gameState));

        roomGameProgressService.endQuestionByTimeout(25L, 0);

        ArgumentCaptor<RoomGameState> gameStateCaptor = ArgumentCaptor.forClass(RoomGameState.class);
        verify(roomRedisRepository).saveGameState(gameStateCaptor.capture());
        assertThat(gameStateCaptor.getValue().currentQuestionEndedAt()).isNotNull();

        verify(roomEventPublisher).publish(org.mockito.ArgumentMatchers.argThat(events ->
                events.size() == 1
                        && events.getFirst().type() == RoomDomainEventType.QUESTION_ENDED
                        && events.getFirst().questionNumber() == 1
        ));
    }

    private RoomState room() {
        return RoomState.waiting(
                25L,
                "방",
                15L,
                "맵",
                null,
                7L,
                "음악",
                10,
                1,
                false,
                null,
                10,
                5,
                30,
                TimeLimitMode.FIXED,
                true,
                true,
                10,
                new RoomMember(3L, "tester3", null, true, now()),
                now()
        ).started("seed", now());
    }

    private RoomGameState gameState() {
        return RoomGameState.started(25L, "seed", List.of(question()), Map.of(3L, 0), now());
    }

    private RoomGameState gameState(RoomGameQuestion question) {
        return RoomGameState.started(25L, "seed", List.of(question), Map.of(3L, 0), now());
    }

    private RoomGameQuestion question() {
        return textQuestion();
    }

    private RoomGameQuestion textQuestion() {
        return new RoomGameQuestion(
                1L,
                1,
                "문제",
                List.of("정답"),
                "정답",
                null,
                null,
                null,
                null,
                null
        );
    }

    private RoomGameQuestion audioQuestion(Integer mediaDurationMs) {
        return new RoomGameQuestion(
                1L,
                1,
                "문제",
                List.of("정답"),
                "정답",
                "https://example.com/audio.mp3",
                "YOUTUBE",
                null,
                null,
                mediaDurationMs
        );
    }

    private LocalDateTime now() {
        return LocalDateTime.of(2026, 8, 21, 20, 0);
    }
}
