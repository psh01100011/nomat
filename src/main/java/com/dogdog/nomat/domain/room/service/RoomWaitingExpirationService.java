package com.dogdog.nomat.domain.room.service;

import com.dogdog.nomat.domain.room.config.RoomLifecycleProperties;
import com.dogdog.nomat.domain.room.model.RoomClosedReason;
import com.dogdog.nomat.domain.room.model.RoomDomainEvent;
import com.dogdog.nomat.domain.room.model.RoomGameTimer;
import com.dogdog.nomat.domain.room.model.RoomGameTimerType;
import com.dogdog.nomat.domain.room.model.RoomState;
import com.dogdog.nomat.domain.room.model.RoomStatus;
import com.dogdog.nomat.domain.room.repository.RoomGameTimerRepository;
import com.dogdog.nomat.domain.room.repository.RoomRedisRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoomWaitingExpirationService {

    private static final int WAITING_TIMER_QUESTION_INDEX = -1;

    private final RoomLifecycleProperties lifecycleProperties;
    private final RoomGameTimerRepository roomGameTimerRepository;
    private final RoomRedisRepository roomRedisRepository;
    private final RoomMutationLock roomMutationLock;
    private final RoomEventPublisher roomEventPublisher;

    public void schedule(RoomState room) {
        roomGameTimerRepository.schedule(new RoomGameTimer(
                room.roomId(),
                WAITING_TIMER_QUESTION_INDEX,
                RoomGameTimerType.WAITING_TIMEOUT,
                room.createdAt()
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .plus(lifecycleProperties.waitingMaxLifetime())
        ));
    }

    public void cancel(Long roomId) {
        roomGameTimerRepository.removeWaitingTimeout(roomId);
    }

    public void expire(Long roomId) {
        roomMutationLock.execute(roomId, () -> expireLocked(roomId));
    }

    private void expireLocked(Long roomId) {
        RoomState room = roomRedisRepository.findById(roomId).orElse(null);
        if (room == null || room.status() != RoomStatus.WAITING) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        roomRedisRepository.deleteRoom(room.closed(now));
        roomEventPublisher.publish(List.of(
                RoomDomainEvent.roomClosed(roomId, RoomClosedReason.WAITING_TIMEOUT, now)
        ));
        log.info("event=room_waiting_timeout roomId={} memberCount={}", roomId, room.memberCount());
    }
}
