package com.dogdog.nomat.domain.room.repository;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.dogdog.nomat.domain.room.config.RoomLifecycleProperties;
import com.dogdog.nomat.domain.room.model.RoomGameTimer;
import com.dogdog.nomat.domain.room.model.RoomGameTimerType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RoomGameTimerRepositoryTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private RoomLifecycleProperties lifecycleProperties;

    @InjectMocks
    private RoomGameTimerRepository repository;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient()
                .when(lifecycleProperties.inactivityTtl())
                .thenReturn(Duration.ofMinutes(15));
    }

    @Test
    void scheduleStoresTimerInGlobalAndRoomIndexes() {
        RoomGameTimer timer = new RoomGameTimer(
                25L,
                0,
                RoomGameTimerType.QUESTION_TIMEOUT,
                Instant.ofEpochMilli(1_000L)
        );

        repository.schedule(timer);

        verify(redisTemplate).execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of("rooms:game-timers:due-at", "rooms:game-timers:room:25")),
                eq("1000"),
                eq("25:0:QUESTION_TIMEOUT"),
                eq("900000")
        );
    }

    @Test
    void removeQuestionTimersRemovesHintAndTimeoutIndexes() {
        repository.removeQuestionTimers(25L, 3);

        verify(redisTemplate, times(2)).execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of("rooms:game-timers:due-at", "rooms:game-timers:room:25")),
                ArgumentMatchers.anyString()
        );
    }

    @Test
    void removeOnlyDeletesTheMatchingReservationTime() {
        RoomGameTimer timer = new RoomGameTimer(
                25L,
                3,
                RoomGameTimerType.HINT,
                Instant.ofEpochMilli(2_000L)
        );

        repository.remove(timer);

        verify(redisTemplate).execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of("rooms:game-timers:due-at", "rooms:game-timers:room:25")),
                eq("25:3:HINT"),
                eq("2000")
        );
    }
}
