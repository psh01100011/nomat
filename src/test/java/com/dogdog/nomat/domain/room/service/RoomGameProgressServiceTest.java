package com.dogdog.nomat.domain.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import com.dogdog.nomat.domain.auth.model.AuthenticatedUserType;
import com.dogdog.nomat.domain.game.entity.TimeLimitMode;
import com.dogdog.nomat.domain.room.dto.RoomAudioLoadFailureRequest;
import com.dogdog.nomat.domain.room.dto.RoomSkipVoteRequest;
import com.dogdog.nomat.domain.room.model.RoomDomainEventType;
import com.dogdog.nomat.domain.room.model.RoomEndedReason;
import com.dogdog.nomat.domain.room.model.RoomGameQuestion;
import com.dogdog.nomat.domain.room.model.RoomGameState;
import com.dogdog.nomat.domain.room.model.RoomGameTimer;
import com.dogdog.nomat.domain.room.model.RoomGameTimerType;
import com.dogdog.nomat.domain.room.model.RoomMember;
import com.dogdog.nomat.domain.room.model.RoomState;
import com.dogdog.nomat.domain.room.repository.RoomRedisRepository;
import com.dogdog.nomat.domain.room.repository.RoomGameTimerRepository;
import com.dogdog.nomat.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomGameProgressServiceTest {

    @Mock
    private RoomRedisRepository roomRedisRepository;

    @Mock
    private RoomGameTimerRepository roomGameTimerRepository;

    @Mock
    private RoomEventPublisher roomEventPublisher;

    @Mock
    private RoomGameResultService roomGameResultService;

    @InjectMocks
    private RoomGameProgressService roomGameProgressService;

    @Test
    void startFirstQuestionSavesCurrentQuestionAndPublishesQuestionStarted() {
        RoomState room = room();
        RoomGameState gameState = gameState();
        given(roomRedisRepository.findGameState(25L)).willReturn(Optional.of(gameState));
        stubStartedQuestion(gameState, 0, 30);

        roomGameProgressService.startFirstQuestion(room);

        verify(roomRedisRepository).tryStartQuestion(eq(25L), eq(0), any(LocalDateTime.class), eq(30));

        ArgumentCaptor<RoomGameTimer> timerCaptor = ArgumentCaptor.forClass(RoomGameTimer.class);
        verify(roomGameTimerRepository, times(2)).schedule(timerCaptor.capture());
        assertThat(timerCaptor.getAllValues())
                .extracting(RoomGameTimer::type)
                .containsExactly(RoomGameTimerType.HINT, RoomGameTimerType.QUESTION_TIMEOUT);

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
    }

    @Test
    void startFirstQuestionUsesAnswerTimeLimitForTextQuestion() {
        RoomState room = room();
        RoomGameState gameState = gameState(textQuestion());
        given(roomRedisRepository.findGameState(25L)).willReturn(Optional.of(gameState));
        stubStartedQuestion(gameState, 0, 30);

        roomGameProgressService.startFirstQuestion(room);

        verify(roomRedisRepository).tryStartQuestion(eq(25L), eq(0), any(LocalDateTime.class), eq(30));
    }

    @Test
    void startFirstQuestionUsesAnswerTimeLimitWhenAudioIsShorterThanLimit() {
        RoomState room = room();
        RoomGameState gameState = gameState(audioQuestion(12_000));
        given(roomRedisRepository.findGameState(25L)).willReturn(Optional.of(gameState));
        stubStartedQuestion(gameState, 0, 30);

        roomGameProgressService.startFirstQuestion(room);

        verify(roomRedisRepository).tryStartQuestion(eq(25L), eq(0), any(LocalDateTime.class), eq(30));
    }

    @Test
    void startFirstQuestionUsesAudioDurationWhenAudioIsLongerThanLimit() {
        RoomState room = room();
        RoomGameState gameState = gameState(audioQuestion(45_000));
        given(roomRedisRepository.findGameState(25L)).willReturn(Optional.of(gameState));
        stubStartedQuestion(gameState, 0, 45);

        roomGameProgressService.startFirstQuestion(room);

        verify(roomRedisRepository).tryStartQuestion(eq(25L), eq(0), any(LocalDateTime.class), eq(45));
    }

    @Test
    void startFirstQuestionRoundsUpAudioDurationSeconds() {
        RoomState room = room();
        RoomGameState gameState = gameState(audioQuestion(30_500));
        given(roomRedisRepository.findGameState(25L)).willReturn(Optional.of(gameState));
        stubStartedQuestion(gameState, 0, 31);

        roomGameProgressService.startFirstQuestion(room);

        verify(roomRedisRepository).tryStartQuestion(eq(25L), eq(0), any(LocalDateTime.class), eq(31));
    }

    @Test
    void revealHintSavesGameStateAndPublishesHint() {
        RoomGameState gameState = gameState().withStartedQuestion(0, now(), 30);
        RoomGameState hintedGameState = gameState.withHintRevealed();
        given(roomRedisRepository.tryRevealHint(25L, 0)).willReturn(Optional.of(hintedGameState));

        roomGameProgressService.revealHint(25L, 0);

        assertThat(hintedGameState.hintRevealed()).isTrue();

        verify(roomEventPublisher).publish(org.mockito.ArgumentMatchers.argThat(events ->
                events.size() == 1
                        && events.getFirst().type() == RoomDomainEventType.HINT_REVEALED
                        && events.getFirst().hint().equals("ㅈㄷ")
        ));
    }

    @Test
    void endQuestionByTimeoutSavesGameStateAndPublishesQuestionEnded() {
        RoomGameState gameState = gameState().withStartedQuestion(0, now(), 30);
        RoomGameState endedGameState = gameState.withQuestionEnded(now().plusSeconds(30));
        given(roomRedisRepository.tryEndQuestionByTimeout(25L, 0)).willReturn(Optional.of(endedGameState));

        roomGameProgressService.endQuestionByTimeout(25L, 0);

        assertThat(endedGameState.currentQuestionEndedAt()).isNotNull();

        verify(roomEventPublisher).publish(org.mockito.ArgumentMatchers.argThat(events ->
                events.size() == 1
                        && events.getFirst().type() == RoomDomainEventType.QUESTION_ENDED
                        && events.getFirst().questionNumber() == 1
        ));
        InOrder timerOrder = inOrder(roomGameTimerRepository);
        timerOrder.verify(roomGameTimerRepository).schedule(org.mockito.ArgumentMatchers.argThat(timer ->
                timer.roomId().equals(25L)
                        && timer.questionIndex() == 0
                        && timer.type() == RoomGameTimerType.NEXT_QUESTION
        ));
        timerOrder.verify(roomGameTimerRepository).removeQuestionTimers(25L, 0);
    }

    @Test
    void voteToSkipSavesVoteAndPublishesVoteStatusBeforeThreshold() {
        RoomState room = room(3).withJoinedMember(
                new RoomMember(-1L, "손님", null, AuthenticatedUserType.GUEST, false, now())
        );
        RoomGameState gameState = gameState().withStartedQuestion(0, now(), 30);
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(room));
        given(roomRedisRepository.findGameState(25L)).willReturn(Optional.of(gameState));
        RoomGameState votedGameState = gameState.withSkipVote(-1L);
        given(roomRedisRepository.tryVoteToSkip(
                eq(25L), eq(0), eq(1L), eq(-1L), eq(3), any(LocalDateTime.class)
        )).willReturn(Optional.of(votedGameState));

        roomGameProgressService.voteToSkip(-1L, 25L, skipVoteRequest(1L, 1));

        assertThat(votedGameState.currentQuestionSkipVoterUserIds()).containsExactly(-1L);
        assertThat(votedGameState.currentQuestionEndedAt()).isNull();

        verify(roomEventPublisher).publish(org.mockito.ArgumentMatchers.argThat(events ->
                events.size() == 1
                        && events.getFirst().type() == RoomDomainEventType.SKIP_VOTE_UPDATED
                        && events.getFirst().userId().equals(-1L)
                        && events.getFirst().userType().equals("GUEST")
                        && events.getFirst().questionNumber() == 1
                        && events.getFirst().memberCount() == 4
                        && events.getFirst().skipVoteCount() == 1
                        && events.getFirst().skipVoteThreshold() == 3
        ));
    }

    @Test
    void voteToSkipEndsQuestionAsSkippedWhenVotesReachThreshold() {
        RoomState room = room(4);
        RoomGameState gameState = gameState()
                .withStartedQuestion(0, now(), 30)
                .withSkipVote(3L)
                .withSkipVote(4L);
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(room));
        given(roomRedisRepository.findGameState(25L)).willReturn(Optional.of(gameState));
        RoomGameState skippedGameState = gameState.withSkipVote(5L).withSkippedQuestion(now());
        given(roomRedisRepository.tryVoteToSkip(
                eq(25L), eq(0), eq(1L), eq(5L), eq(3), any(LocalDateTime.class)
        )).willReturn(Optional.of(skippedGameState));

        roomGameProgressService.voteToSkip(5L, 25L, skipVoteRequest(1L, 1));

        assertThat(skippedGameState.currentQuestionSkipVoterUserIds()).containsExactlyInAnyOrder(3L, 4L, 5L);
        assertThat(skippedGameState.currentQuestionEndedAt()).isNotNull();
        assertThat(skippedGameState.questionOutcomes().get(1).endedReason()).isEqualTo("SKIPPED");

        verify(roomEventPublisher).publish(org.mockito.ArgumentMatchers.argThat(events ->
                events.size() == 2
                        && events.get(0).type() == RoomDomainEventType.SKIP_VOTE_UPDATED
                        && events.get(0).skipVoteCount() == 3
                        && events.get(0).skipVoteThreshold() == 3
                        && events.get(1).type() == RoomDomainEventType.QUESTION_ENDED
                        && events.get(1).questionNumber() == 1
        ));
        verify(roomGameTimerRepository).schedule(any(RoomGameTimer.class));
    }

    @Test
    void voteToSkipRejectsStaleQuestionVote() {
        RoomState room = room(4);
        RoomGameState gameState = twoQuestionGameState().withStartedQuestion(1, now(), 30);
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(room));
        given(roomRedisRepository.findGameState(25L)).willReturn(Optional.of(gameState));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        roomGameProgressService.voteToSkip(4L, 25L, skipVoteRequest(1L, 1))
                )
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("stale_question");
    }

    @Test
    void reportAudioLoadFailureEndsOnlyCurrentQuestionWithoutRevealingAnswer() {
        RoomState room = room();
        RoomGameState gameState = gameState(audioQuestion(30_000)).withStartedQuestion(0, now(), 30);
        RoomGameState failedGameState = gameState.withAudioLoadFailed(now().plusSeconds(1));
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(room));
        given(roomRedisRepository.findGameState(25L)).willReturn(Optional.of(gameState));
        given(roomRedisRepository.tryEndQuestionForAudioLoadFailure(25L, 0, 1L))
                .willReturn(Optional.of(failedGameState));

        roomGameProgressService.reportAudioLoadFailure(3L, 25L, audioLoadFailureRequest(1L, 1));

        assertThat(failedGameState.questionOutcomes().get(1).endedReason()).isEqualTo("AUDIO_LOAD_FAILED");
        assertThat(failedGameState.questionOutcomes().get(1).winnerAnswer()).isNull();
        verify(roomEventPublisher).publish(org.mockito.ArgumentMatchers.argThat(events ->
                events.size() == 1
                        && events.getFirst().type() == RoomDomainEventType.QUESTION_AUDIO_LOAD_FAILED
                        && events.getFirst().questionId().equals(1L)
                        && events.getFirst().questionNumber() == 1
                        && events.getFirst().content().contains("오디오 파일")
        ));
        verify(roomGameTimerRepository).schedule(any(RoomGameTimer.class));
    }

    @Test
    void reportAudioLoadFailureDoesNothingWhenQuestionWasAlreadyEnded() {
        RoomState room = room();
        RoomGameState gameState = gameState(audioQuestion(30_000)).withStartedQuestion(0, now(), 30);
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(room));
        given(roomRedisRepository.findGameState(25L)).willReturn(Optional.of(gameState));
        given(roomRedisRepository.tryEndQuestionForAudioLoadFailure(25L, 0, 1L))
                .willReturn(Optional.empty());

        roomGameProgressService.reportAudioLoadFailure(3L, 25L, audioLoadFailureRequest(1L, 1));

        verify(roomEventPublisher, never()).publish(any());
        verify(roomGameTimerRepository, never()).schedule(any(RoomGameTimer.class));
    }

    @Test
    void continueAfterQuestionEndedStartsNextQuestion() {
        RoomState room = room();
        RoomGameState endedGameState = twoQuestionGameState()
                .withStartedQuestion(0, now(), 30)
                .withQuestionEnded(now().plusSeconds(30));
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(room));
        given(roomRedisRepository.findGameState(25L)).willReturn(Optional.of(endedGameState));
        stubStartedQuestion(endedGameState, 1, 30);

        roomGameProgressService.continueAfterQuestionEnded(25L, 0);

        verify(roomRedisRepository).tryStartQuestion(eq(25L), eq(1), any(LocalDateTime.class), eq(30));

        verify(roomEventPublisher).publish(org.mockito.ArgumentMatchers.argThat(events ->
                events.size() == 1
                        && events.getFirst().type() == RoomDomainEventType.QUESTION_STARTED
                        && events.getFirst().questionNumber() == 2
        ));
        verify(roomGameTimerRepository, times(2)).schedule(any(RoomGameTimer.class));
    }

    @Test
    void continueAfterLastQuestionEndedEndsGame() {
        RoomState room = room();
        RoomGameState endedGameState = gameState()
                .withStartedQuestion(0, now(), 30)
                .withQuestionEnded(now().plusSeconds(30));
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(room));
        given(roomRedisRepository.findGameState(25L)).willReturn(Optional.of(endedGameState));

        roomGameProgressService.continueAfterQuestionEnded(25L, 0);

        verify(roomGameResultService).endGame(25L, null, RoomEndedReason.COMPLETED);
    }

    private RoomState room() {
        return room(1);
    }

    private void stubStartedQuestion(RoomGameState gameState, int questionIndex, int durationSeconds) {
        given(roomRedisRepository.tryStartQuestion(
                eq(25L),
                eq(questionIndex),
                any(LocalDateTime.class),
                eq(durationSeconds)
        )).willAnswer(invocation -> Optional.of(gameState.withStartedQuestion(
                questionIndex,
                invocation.getArgument(2, LocalDateTime.class),
                durationSeconds
        )));
    }

    private RoomState room(int memberCount) {
        RoomState room = RoomState.waiting(
                25L,
                "방",
                15L,
                "맵",
                "AUDIO",
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
        );

        for (long userId = 4L; userId < 3L + memberCount; userId++) {
            room = room.withJoinedMember(new RoomMember(userId, "tester" + userId, null, false, now()));
        }

        return room.started("seed", now());
    }

    private RoomGameState gameState() {
        return RoomGameState.started(25L, "seed", List.of(question()), Map.of(3L, 0), now());
    }

    private RoomGameState gameState(RoomGameQuestion question) {
        return RoomGameState.started(25L, "seed", List.of(question), Map.of(3L, 0), now());
    }

    private RoomGameState twoQuestionGameState() {
        return RoomGameState.started(25L, "seed", List.of(question(), secondQuestion()), Map.of(3L, 0), now());
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

    private RoomGameQuestion secondQuestion() {
        return new RoomGameQuestion(
                2L,
                2,
                "두 번째 문제",
                List.of("정답2"),
                "정답2",
                null,
                null,
                null,
                null,
                null
        );
    }

    private RoomSkipVoteRequest skipVoteRequest(Long questionId, Integer questionNumber) {
        return new RoomSkipVoteRequest(questionId, questionNumber);
    }

    private RoomAudioLoadFailureRequest audioLoadFailureRequest(Long questionId, Integer questionNumber) {
        return new RoomAudioLoadFailureRequest(questionId, questionNumber);
    }

    private LocalDateTime now() {
        return LocalDateTime.of(2026, 8, 21, 20, 0);
    }
}
