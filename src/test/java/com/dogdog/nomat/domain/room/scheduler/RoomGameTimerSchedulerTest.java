package com.dogdog.nomat.domain.room.scheduler;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.dogdog.nomat.domain.room.config.RoomGameTimerProperties;
import com.dogdog.nomat.domain.room.model.ClaimedRoomGameTimer;
import com.dogdog.nomat.domain.room.model.RoomGameTimer;
import com.dogdog.nomat.domain.room.model.RoomGameTimerType;
import com.dogdog.nomat.domain.room.repository.RoomGameTimerRepository;
import com.dogdog.nomat.domain.room.service.RoomGameProgressService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomGameTimerSchedulerTest {

    @Mock
    private RoomGameTimerRepository roomGameTimerRepository;

    @Mock
    private RoomGameProgressService roomGameProgressService;

    @Mock
    private RoomGameTimerProperties properties;

    @InjectMocks
    private RoomGameTimerScheduler scheduler;

    @Test
    void processDueTimersExecutesEveryClaimedTimerEvenWhenOneFails() {
        RoomGameTimer failedTimer = new RoomGameTimer(
                25L, 0, RoomGameTimerType.HINT, Instant.ofEpochMilli(1_000L)
        );
        RoomGameTimer nextTimer = new RoomGameTimer(
                26L, 1, RoomGameTimerType.QUESTION_TIMEOUT, Instant.ofEpochMilli(2_000L)
        );
        ClaimedRoomGameTimer failedClaim = new ClaimedRoomGameTimer(failedTimer, "owner-token");
        ClaimedRoomGameTimer nextClaim = new ClaimedRoomGameTimer(nextTimer, "owner-token");
        given(properties.getBatchSize()).willReturn(100);
        given(properties.leaseDuration()).willReturn(Duration.ofSeconds(5));
        given(roomGameTimerRepository.claimDue(
                ArgumentMatchers.any(Instant.class),
                ArgumentMatchers.eq(Duration.ofSeconds(5)),
                ArgumentMatchers.eq(100)
        )).willReturn(List.of(failedClaim, nextClaim));
        doThrow(new IllegalStateException("failed"))
                .when(roomGameProgressService).executeTimer(failedTimer);

        scheduler.processDueTimers();

        verify(roomGameProgressService).executeTimer(failedTimer);
        verify(roomGameProgressService).executeTimer(nextTimer);
        verify(roomGameTimerRepository, never()).complete(failedClaim);
        verify(roomGameTimerRepository).complete(nextClaim);
    }
}
