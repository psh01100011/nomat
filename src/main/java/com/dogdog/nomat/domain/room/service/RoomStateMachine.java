package com.dogdog.nomat.domain.room.service;

import com.dogdog.nomat.domain.room.model.RoomClosedReason;
import com.dogdog.nomat.domain.room.model.RoomCommand;
import com.dogdog.nomat.domain.room.model.RoomCommandType;
import com.dogdog.nomat.domain.room.model.RoomDomainEvent;
import com.dogdog.nomat.domain.room.model.RoomEndedReason;
import com.dogdog.nomat.domain.room.model.RoomMember;
import com.dogdog.nomat.domain.room.model.RoomState;
import com.dogdog.nomat.domain.room.model.RoomStatus;
import com.dogdog.nomat.domain.room.model.RoomTransitionResult;
import com.dogdog.nomat.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RoomStateMachine {

    public RoomTransitionResult transition(RoomState room, RoomCommand command) {
        return switch (command.type()) {
            case JOIN -> join(room, command);
            case LEAVE -> leave(room, command);
            case KICK -> kick(room, command);
            case START -> start(room, command);
            case CLOSE -> close(room, command);
            case END_GAME -> endGame(room, command);
        };
    }

    private RoomTransitionResult join(RoomState room, RoomCommand command) {
        requireStatus(room, RoomStatus.WAITING, "cannot_join_room");

        RoomMember member = command.member();
        if (member == null) {
            throw invalidRequest();
        }

        if (room.hasMember(member.userId()) || room.isFull()) {
            throw new BusinessException(HttpStatus.CONFLICT, "cannot_join_room");
        }

        if (room.isKicked(member.userId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "room_access_denied");
        }

        RoomState nextRoom = room.withJoinedMember(member);
        return RoomTransitionResult.of(
                nextRoom,
                RoomDomainEvent.memberJoined(nextRoom, member.userId(), occurredAt(command))
        );
    }

    private RoomTransitionResult leave(RoomState room, RoomCommand command) {
        if (room.status() == RoomStatus.CLOSED || room.status() == RoomStatus.ENDED) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "room_or_member_not_found");
        }

        if (!room.hasMember(command.userId())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "room_or_member_not_found");
        }

        LocalDateTime occurredAt = occurredAt(command);
        Long previousHostUserId = room.hostUserId();
        Long nextHostUserId = room.isHost(command.userId())
                ? room.nextHostAfter(command.userId()).map(RoomMember::userId).orElse(null)
                : previousHostUserId;

        if (nextHostUserId == null) {
            RoomState closedRoom = room.withoutMember(command.userId(), null).closed(occurredAt);
            return RoomTransitionResult.of(
                    closedRoom,
                    List.of(
                            RoomDomainEvent.memberLeft(closedRoom, command.userId(), occurredAt),
                            RoomDomainEvent.roomClosed(room.roomId(), RoomClosedReason.ALL_LEFT, occurredAt)
                    )
            );
        }

        RoomState nextRoom = room.withoutMember(command.userId(), nextHostUserId);
        List<RoomDomainEvent> events = new ArrayList<>();
        events.add(RoomDomainEvent.memberLeft(nextRoom, command.userId(), occurredAt));
        if (!previousHostUserId.equals(nextHostUserId)) {
            events.add(RoomDomainEvent.hostChanged(room.roomId(), previousHostUserId, nextHostUserId, occurredAt));
        }

        return RoomTransitionResult.of(nextRoom, events);
    }

    private RoomTransitionResult kick(RoomState room, RoomCommand command) {
        requireStatus(room, RoomStatus.WAITING, "cannot_kick_room_member");
        requireHost(room, command.userId());

        Long targetUserId = command.targetUserId();
        if (targetUserId == null || !room.hasMember(targetUserId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "room_or_member_not_found");
        }

        if (room.isHost(targetUserId)) {
            throw new BusinessException(HttpStatus.CONFLICT, "cannot_kick_room_member");
        }

        RoomState nextRoom = room.withKickedMember(targetUserId);
        return RoomTransitionResult.of(
                nextRoom,
                RoomDomainEvent.memberKicked(nextRoom, targetUserId, occurredAt(command))
        );
    }

    private RoomTransitionResult start(RoomState room, RoomCommand command) {
        requireStatus(room, RoomStatus.WAITING, "cannot_start_game");
        requireHost(room, command.userId());

        if (!StringUtils.hasText(command.randomSeed())) {
            throw invalidRequest();
        }

        RoomState nextRoom = room.started(command.randomSeed(), occurredAt(command));
        return RoomTransitionResult.of(
                nextRoom,
                RoomDomainEvent.gameStarted(nextRoom, occurredAt(command))
        );
    }

    private RoomTransitionResult close(RoomState room, RoomCommand command) {
        requireStatus(room, RoomStatus.WAITING, "room_already_started");
        requireHost(room, command.userId());

        LocalDateTime occurredAt = occurredAt(command);
        RoomState closedRoom = room.closed(occurredAt);
        return RoomTransitionResult.of(
                closedRoom,
                RoomDomainEvent.roomClosed(room.roomId(), RoomClosedReason.HOST_CLOSED, occurredAt)
        );
    }

    private RoomTransitionResult endGame(RoomState room, RoomCommand command) {
        requireStatus(room, RoomStatus.PLAYING, "cannot_start_game");

        LocalDateTime occurredAt = occurredAt(command);
        RoomState endedRoom = room.ended(occurredAt);
        return RoomTransitionResult.of(
                endedRoom,
                RoomDomainEvent.gameEnded(endedRoom, RoomEndedReason.COMPLETED, occurredAt)
        );
    }

    private void requireStatus(RoomState room, RoomStatus expectedStatus, String messageCode) {
        if (room.status() != expectedStatus) {
            throw new BusinessException(HttpStatus.CONFLICT, messageCode);
        }
    }

    private void requireHost(RoomState room, Long userId) {
        if (!room.isHost(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "forbidden_room_access");
        }
    }

    private LocalDateTime occurredAt(RoomCommand command) {
        return command.requestedAt() == null ? LocalDateTime.now() : command.requestedAt();
    }

    private BusinessException invalidRequest() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "invalid_request");
    }
}
