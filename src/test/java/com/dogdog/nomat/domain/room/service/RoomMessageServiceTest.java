package com.dogdog.nomat.domain.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.dogdog.nomat.domain.auth.model.AuthenticatedUserType;
import com.dogdog.nomat.domain.game.entity.TimeLimitMode;
import com.dogdog.nomat.domain.room.dto.RoomChatMessageRequest;
import com.dogdog.nomat.domain.room.model.RoomDomainEventType;
import com.dogdog.nomat.domain.room.model.RoomGameQuestion;
import com.dogdog.nomat.domain.room.model.RoomGameState;
import com.dogdog.nomat.domain.room.model.RoomMember;
import com.dogdog.nomat.domain.room.model.RoomState;
import com.dogdog.nomat.domain.room.repository.RoomRedisRepository;
import com.dogdog.nomat.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomMessageServiceTest {

    @Mock
    private RoomRedisRepository roomRedisRepository;

    @Mock
    private RoomEventPublisher roomEventPublisher;

    @Mock
    private RoomGameProgressService roomGameProgressService;

    @Mock
    private RoomMessageRateLimiter roomMessageRateLimiter;

    @InjectMocks
    private RoomMessageService roomMessageService;

    @Test
    void sendMessagePublishesGuestChatMessageInWaitingRoom() {
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(guestRoom()));

        roomMessageService.sendMessage(-1L, 25L, new RoomChatMessageRequest(" 안녕 ", " message-1 "));

        verify(roomEventPublisher).publish(org.mockito.ArgumentMatchers.argThat(events ->
                events.size() == 1
                        && events.getFirst().type() == RoomDomainEventType.CHAT_MESSAGE
                        && events.getFirst().userId().equals(-1L)
                        && events.getFirst().nickname().equals("손님")
                        && events.getFirst().userType().equals("GUEST")
                        && events.getFirst().content().equals("안녕")
                        && events.getFirst().clientMessageId().equals("message-1")
        ));
        verify(roomRedisRepository, never()).saveGameState(org.mockito.ArgumentMatchers.any(RoomGameState.class));
    }

    @Test
    void sendMessageRejectsNonMemberOrBlankMessage() {
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(room()));

        assertThatThrownBy(() -> roomMessageService.sendMessage(4L, 25L, new RoomChatMessageRequest("안녕")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("forbidden_room_access");

        assertThatThrownBy(() -> roomMessageService.sendMessage(3L, 25L, new RoomChatMessageRequest(" ")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_request");
    }

    @Test
    void sendMessageRejectsRateLimitedMessageBeforePublishing() {
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(room()));
        doThrow(new BusinessException(
                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                "message_rate_limited",
                new RoomMessageRateLimiter.RateLimitData(1, "message-1")
        )).when(roomMessageRateLimiter).checkAllowed(25L, 3L, "message-1");

        assertThatThrownBy(() -> roomMessageService.sendMessage(3L, 25L, new RoomChatMessageRequest("정답", "message-1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("message_rate_limited");

        verify(roomEventPublisher, never()).publish(org.mockito.ArgumentMatchers.anyList());
        verify(roomRedisRepository, never()).saveGameState(org.mockito.ArgumentMatchers.any(RoomGameState.class));
    }

    @Test
    void sendMessageRecordsGuestCorrectAnswerInPlayingRoom() {
        RoomState playingRoom = guestRoom().started("seed", now());
        RoomGameState gameState = gameState(-1L);
        RoomGameState savedGameState = gameState.withCorrectAnswer(-1L, "정 답", 100);
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(playingRoom));
        given(roomRedisRepository.findGameState(25L)).willReturn(Optional.of(gameState));
        given(roomRedisRepository.tryRecordCorrectAnswer(25L, 0, "정답", -1L, "정 답", 100))
                .willReturn(Optional.of(savedGameState));

        roomMessageService.sendMessage(-1L, 25L, new RoomChatMessageRequest("정 답"));

        assertThat(savedGameState.currentQuestionWinnerUserId()).isEqualTo(-1L);
        assertThat(savedGameState.currentQuestionWinnerAnswer()).isEqualTo("정 답");
        assertThat(savedGameState.scores()).containsEntry(-1L, 100);

        verify(roomEventPublisher).publish(org.mockito.ArgumentMatchers.argThat(events ->
                events.size() == 4
                        && events.get(0).type() == RoomDomainEventType.CHAT_MESSAGE
                        && events.get(0).userType().equals("GUEST")
                        && events.get(1).type() == RoomDomainEventType.CORRECT_ANSWER
                        && events.get(1).userType().equals("GUEST")
                        && events.get(2).type() == RoomDomainEventType.SCORE_UPDATED
                        && events.get(2).userType().equals("GUEST")
                        && events.get(2).score() == 100
                        && events.get(3).type() == RoomDomainEventType.QUESTION_ENDED
        ));
        verify(roomGameProgressService).scheduleQuestionAdvance(25L, 0);
    }

    @Test
    void sendMessagePublishesOnlyChatMessageWhenCorrectAnswerRaceLost() {
        RoomState playingRoom = room().started("seed", now());
        RoomGameState gameState = gameState();
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(playingRoom));
        given(roomRedisRepository.findGameState(25L)).willReturn(Optional.of(gameState));
        given(roomRedisRepository.tryRecordCorrectAnswer(25L, 0, "정답", 3L, "정답", 100))
                .willReturn(Optional.empty());

        roomMessageService.sendMessage(3L, 25L, new RoomChatMessageRequest("정답"));

        verify(roomEventPublisher).publish(org.mockito.ArgumentMatchers.argThat(events ->
                events.size() == 1
                        && events.getFirst().type() == RoomDomainEventType.CHAT_MESSAGE
                        && events.getFirst().content().equals("정답")
        ));
        verify(roomRedisRepository, never()).saveGameState(org.mockito.ArgumentMatchers.any(RoomGameState.class));
        verify(roomGameProgressService, never()).scheduleQuestionAdvance(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt()
        );
    }

    @Test
    void sendMessageDoesNotRecordCorrectAnswerWithoutActiveQuestionOrAfterWinnerExists() {
        RoomState playingRoom = room().started("seed", now());
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(playingRoom));
        given(roomRedisRepository.findGameState(25L)).willReturn(Optional.of(gameStateWithoutActiveQuestion()));

        roomMessageService.sendMessage(3L, 25L, new RoomChatMessageRequest("정답"));

        verify(roomRedisRepository, never()).saveGameState(org.mockito.ArgumentMatchers.any(RoomGameState.class));

        given(roomRedisRepository.findById(26L)).willReturn(Optional.of(playingRoom));
        given(roomRedisRepository.findGameState(26L)).willReturn(Optional.of(gameStateWithWinner()));
        roomMessageService.sendMessage(3L, 26L, new RoomChatMessageRequest("정답"));

        verify(roomRedisRepository, never()).saveGameState(org.mockito.ArgumentMatchers.any(RoomGameState.class));
        verify(roomRedisRepository, never()).tryRecordCorrectAnswer(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt()
        );
        verify(roomGameProgressService, never()).scheduleQuestionAdvance(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt()
        );
    }

    private RoomState room() {
        return room(new RoomMember(3L, "tester3", null, true, now()));
    }

    private RoomState guestRoom() {
        return room(new RoomMember(
                -1L,
                "손님",
                null,
                AuthenticatedUserType.GUEST,
                true,
                now()
        ));
    }

    private RoomState room(RoomMember hostMember) {
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
                hostMember,
                now()
        );
    }

    private RoomGameState gameState() {
        return gameState(3L);
    }

    private RoomGameState gameState(Long userId) {
        return new RoomGameState(
                25L,
                "seed",
                List.of(question()),
                0,
                now(),
                30,
                now().plusSeconds(30),
                null,
                false,
                null,
                null,
                Set.of(),
                Map.of(userId, 0),
                Map.of(),
                now(),
                null
        );
    }

    private RoomGameState gameStateWithoutActiveQuestion() {
        return new RoomGameState(
                25L,
                "seed",
                List.of(question()),
                -1,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                Set.of(),
                Map.of(3L, 0),
                Map.of(),
                now(),
                null
        );
    }

    private RoomGameState gameStateWithWinner() {
        return new RoomGameState(
                25L,
                "seed",
                List.of(question()),
                0,
                now(),
                30,
                now().plusSeconds(30),
                null,
                false,
                3L,
                "정답",
                Set.of(),
                Map.of(3L, 100),
                Map.of(),
                now(),
                null
        );
    }

    private RoomGameQuestion question() {
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

    private LocalDateTime now() {
        return LocalDateTime.of(2026, 8, 21, 20, 0);
    }
}
