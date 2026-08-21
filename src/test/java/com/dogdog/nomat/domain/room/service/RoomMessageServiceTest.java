package com.dogdog.nomat.domain.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomMessageServiceTest {

    @Mock
    private RoomRedisRepository roomRedisRepository;

    @Mock
    private RoomEventPublisher roomEventPublisher;

    @InjectMocks
    private RoomMessageService roomMessageService;

    @Test
    void sendMessagePublishesChatMessageInWaitingRoom() {
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(room()));

        roomMessageService.sendMessage(3L, 25L, new RoomChatMessageRequest(" 안녕 "));

        verify(roomEventPublisher).publish(org.mockito.ArgumentMatchers.argThat(events ->
                events.size() == 1
                        && events.getFirst().type() == RoomDomainEventType.CHAT_MESSAGE
                        && events.getFirst().userId().equals(3L)
                        && events.getFirst().nickname().equals("tester3")
                        && events.getFirst().content().equals("안녕")
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
    void sendMessageRecordsCorrectAnswerInPlayingRoom() {
        RoomState playingRoom = room().started("seed", now());
        RoomGameState gameState = gameState();
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(playingRoom));
        given(roomRedisRepository.findGameState(25L)).willReturn(Optional.of(gameState));

        roomMessageService.sendMessage(3L, 25L, new RoomChatMessageRequest("정 답"));

        ArgumentCaptor<RoomGameState> gameStateCaptor = ArgumentCaptor.forClass(RoomGameState.class);
        verify(roomRedisRepository).saveGameState(gameStateCaptor.capture());
        RoomGameState savedGameState = gameStateCaptor.getValue();
        assertThat(savedGameState.currentQuestionWinnerUserId()).isEqualTo(3L);
        assertThat(savedGameState.currentQuestionWinnerAnswer()).isEqualTo("정 답");
        assertThat(savedGameState.scores()).containsEntry(3L, 100);

        verify(roomEventPublisher).publish(org.mockito.ArgumentMatchers.argThat(events ->
                events.size() == 4
                        && events.get(0).type() == RoomDomainEventType.CHAT_MESSAGE
                        && events.get(1).type() == RoomDomainEventType.CORRECT_ANSWER
                        && events.get(2).type() == RoomDomainEventType.SCORE_UPDATED
                        && events.get(2).score() == 100
                        && events.get(3).type() == RoomDomainEventType.QUESTION_ENDED
        ));
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
        );
    }

    private RoomGameState gameState() {
        return new RoomGameState(
                25L,
                "seed",
                List.of(question()),
                0,
                now(),
                null,
                false,
                null,
                null,
                Map.of(3L, 0),
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
                false,
                null,
                null,
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
                null,
                false,
                3L,
                "정답",
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
