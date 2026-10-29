package com.dogdog.nomat.domain.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomWaitingExpirationServiceTest {

    @Mock
    private RoomGameTimerRepository roomGameTimerRepository;

    @Mock
    private RoomRedisRepository roomRedisRepository;

    @Mock
    private RoomMutationLock roomMutationLock;

    @Mock
    private RoomEventPublisher roomEventPublisher;

    private final RoomLifecycleProperties lifecycleProperties = new RoomLifecycleProperties();

    private RoomWaitingExpirationService roomWaitingExpirationService;

    @BeforeEach
    void executeRoomMutations() {
        roomWaitingExpirationService = new RoomWaitingExpirationService(
                lifecycleProperties,
                roomGameTimerRepository,
                roomRedisRepository,
                roomMutationLock,
                roomEventPublisher
        );
        org.mockito.Mockito.lenient().doAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return null;
        }).when(roomMutationLock).execute(any(), any());
    }

    @Test
    void scheduleUsesRoomCreationTimeAndWaitingLifetime() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 29, 12, 0);
        RoomState room = org.mockito.Mockito.mock(RoomState.class);
        given(room.roomId()).willReturn(25L);
        given(room.createdAt()).willReturn(createdAt);

        roomWaitingExpirationService.schedule(room);

        ArgumentCaptor<RoomGameTimer> timerCaptor = ArgumentCaptor.forClass(RoomGameTimer.class);
        verify(roomGameTimerRepository).schedule(timerCaptor.capture());
        RoomGameTimer timer = timerCaptor.getValue();
        assertThat(timer.roomId()).isEqualTo(25L);
        assertThat(timer.questionIndex()).isEqualTo(-1);
        assertThat(timer.type()).isEqualTo(RoomGameTimerType.WAITING_TIMEOUT);
        assertThat(timer.dueAt()).isEqualTo(
                createdAt.atZone(ZoneId.systemDefault()).toInstant().plusSeconds(30 * 60)
        );
    }

    @Test
    void expireDeletesWaitingRoomAndPublishesTimeoutEvent() {
        RoomState room = org.mockito.Mockito.mock(RoomState.class);
        RoomState closedRoom = org.mockito.Mockito.mock(RoomState.class);
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(room));
        given(room.status()).willReturn(RoomStatus.WAITING);
        given(room.closed(any(LocalDateTime.class))).willReturn(closedRoom);

        roomWaitingExpirationService.expire(25L);

        verify(roomRedisRepository).deleteRoom(closedRoom);
        ArgumentCaptor<java.util.List<RoomDomainEvent>> eventsCaptor = ArgumentCaptor.forClass(java.util.List.class);
        verify(roomEventPublisher).publish(eventsCaptor.capture());
        assertThat(eventsCaptor.getValue()).singleElement().satisfies(event -> {
            assertThat(event.roomId()).isEqualTo(25L);
            assertThat(event.closedReason()).isEqualTo(RoomClosedReason.WAITING_TIMEOUT);
        });
    }

    @Test
    void expireDoesNotDeletePlayingRoom() {
        RoomState room = org.mockito.Mockito.mock(RoomState.class);
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(room));
        given(room.status()).willReturn(RoomStatus.PLAYING);

        roomWaitingExpirationService.expire(25L);

        verify(roomRedisRepository, never()).deleteRoom(any(RoomState.class));
        verify(roomEventPublisher, never()).publish(any());
    }
}
