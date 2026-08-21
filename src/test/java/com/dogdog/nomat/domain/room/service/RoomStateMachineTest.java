package com.dogdog.nomat.domain.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dogdog.nomat.domain.game.entity.TimeLimitMode;
import com.dogdog.nomat.domain.room.model.RoomCommand;
import com.dogdog.nomat.domain.room.model.RoomClosedReason;
import com.dogdog.nomat.domain.room.model.RoomDomainEventType;
import com.dogdog.nomat.domain.room.model.RoomMember;
import com.dogdog.nomat.domain.room.model.RoomState;
import com.dogdog.nomat.domain.room.model.RoomStatus;
import com.dogdog.nomat.domain.room.model.RoomTransitionResult;
import com.dogdog.nomat.global.exception.BusinessException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class RoomStateMachineTest {

    private final RoomStateMachine stateMachine = new RoomStateMachine();

    @Test
    void joinAddsMemberWhenRoomIsWaiting() {
        RoomState room = waitingRoom();
        RoomMember member = member(2L, "peter", now().plusSeconds(1));

        RoomTransitionResult result = stateMachine.transition(
                room,
                RoomCommand.join(member, now().plusSeconds(1))
        );

        assertThat(result.room().status()).isEqualTo(RoomStatus.WAITING);
        assertThat(result.room().memberCount()).isEqualTo(2);
        assertThat(result.room().hasMember(2L)).isTrue();
        assertThat(result.events()).hasSize(1);
        assertThat(result.events().getFirst().type()).isEqualTo(RoomDomainEventType.MEMBER_JOINED);
        assertThat(result.events().getFirst().userId()).isEqualTo(2L);
    }

    @Test
    void startChangesWaitingRoomToPlaying() {
        RoomState room = waitingRoom();
        LocalDateTime startedAt = now().plusSeconds(3);

        RoomTransitionResult result = stateMachine.transition(
                room,
                RoomCommand.start(1L, "seed-1", startedAt)
        );

        assertThat(result.room().status()).isEqualTo(RoomStatus.PLAYING);
        assertThat(result.room().randomSeed()).isEqualTo("seed-1");
        assertThat(result.room().startedAt()).isEqualTo(startedAt);
        assertThat(result.events()).hasSize(1);
        assertThat(result.events().getFirst().type()).isEqualTo(RoomDomainEventType.GAME_STARTED);
    }

    @Test
    void leaveTransfersHostToOldestRemainingMember() {
        RoomState room = stateMachine.transition(
                waitingRoom(),
                RoomCommand.join(member(2L, "peter", now().plusSeconds(1)), now().plusSeconds(1))
        ).room();
        room = stateMachine.transition(
                room,
                RoomCommand.join(member(3L, "maria", now().plusSeconds(2)), now().plusSeconds(2))
        ).room();

        RoomTransitionResult result = stateMachine.transition(
                room,
                RoomCommand.leave(1L, now().plusSeconds(4))
        );

        assertThat(result.room().hostUserId()).isEqualTo(2L);
        assertThat(result.room().memberCount()).isEqualTo(2);
        assertThat(result.events()).extracting(event -> event.type())
                .containsExactly(RoomDomainEventType.MEMBER_LEFT, RoomDomainEventType.HOST_CHANGED);
        assertThat(result.events().get(1).previousHostUserId()).isEqualTo(1L);
        assertThat(result.events().get(1).newHostUserId()).isEqualTo(2L);
    }

    @Test
    void lastMemberLeaveClosesRoom() {
        RoomTransitionResult result = stateMachine.transition(
                waitingRoom(),
                RoomCommand.leave(1L, now().plusSeconds(4))
        );

        assertThat(result.room().status()).isEqualTo(RoomStatus.CLOSED);
        assertThat(result.room().memberCount()).isZero();
        assertThat(result.events()).extracting(event -> event.type())
                .containsExactly(RoomDomainEventType.MEMBER_LEFT, RoomDomainEventType.ROOM_CLOSED);
        assertThat(result.events().get(1).closedReason()).isEqualTo(RoomClosedReason.ALL_LEFT);
    }

    @Test
    void kickRemovesTargetAndPreventsRejoin() {
        RoomState room = stateMachine.transition(
                waitingRoom(),
                RoomCommand.join(member(2L, "peter", now().plusSeconds(1)), now().plusSeconds(1))
        ).room();

        RoomTransitionResult result = stateMachine.transition(
                room,
                RoomCommand.kick(1L, 2L, now().plusSeconds(2))
        );

        assertThat(result.room().hasMember(2L)).isFalse();
        assertThat(result.room().isKicked(2L)).isTrue();
        assertThat(result.events().getFirst().type()).isEqualTo(RoomDomainEventType.MEMBER_KICKED);

        assertThatThrownBy(() -> stateMachine.transition(
                result.room(),
                RoomCommand.join(member(2L, "peter", now().plusSeconds(3)), now().plusSeconds(3))
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("room_access_denied");
    }

    @Test
    void joinRejectsPlayingRoom() {
        RoomState room = stateMachine.transition(
                waitingRoom(),
                RoomCommand.start(1L, "seed-1", now().plusSeconds(3))
        ).room();

        assertThatThrownBy(() -> stateMachine.transition(
                room,
                RoomCommand.join(member(2L, "peter", now().plusSeconds(4)), now().plusSeconds(4))
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot_join_room");
    }

    @Test
    void kickRejectsNonHost() {
        RoomState room = stateMachine.transition(
                waitingRoom(),
                RoomCommand.join(member(2L, "peter", now().plusSeconds(1)), now().plusSeconds(1))
        ).room();

        assertThatThrownBy(() -> stateMachine.transition(
                room,
                RoomCommand.kick(2L, 1L, now().plusSeconds(2))
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("forbidden_room_access");
    }

    private RoomState waitingRoom() {
        return RoomState.waiting(
                25L,
                "아이돌 노래 맞히기",
                15L,
                "20년대 아이돌 노래 맞히기",
                null,
                300,
                3,
                false,
                null,
                10,
                50,
                30,
                TimeLimitMode.FIXED,
                true,
                true,
                10,
                member(1L, "jason", now()),
                now()
        );
    }

    private RoomMember member(Long userId, String nickname, LocalDateTime joinedAt) {
        return new RoomMember(userId, nickname, null, false, joinedAt);
    }

    private LocalDateTime now() {
        return LocalDateTime.of(2026, 8, 21, 16, 0);
    }
}
