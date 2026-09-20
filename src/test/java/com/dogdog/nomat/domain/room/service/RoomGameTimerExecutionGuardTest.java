package com.dogdog.nomat.domain.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.dogdog.nomat.domain.room.config.RoomGameTimerProperties;
import com.dogdog.nomat.domain.room.config.RoomLifecycleProperties;
import com.dogdog.nomat.domain.room.model.RoomGameTimer;
import com.dogdog.nomat.domain.room.model.RoomGameTimerType;
import com.dogdog.nomat.domain.room.service.RoomGameTimerExecutionGuard.ExecutionStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RoomGameTimerExecutionGuardTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private Runnable action;

    private RoomGameTimerExecutionGuard executionGuard;

    @BeforeEach
    void setUp() {
        RoomGameTimerProperties timerProperties = new RoomGameTimerProperties();
        timerProperties.setExecutionLockDurationMs(30_000);
        RoomLifecycleProperties lifecycleProperties = new RoomLifecycleProperties();
        lifecycleProperties.setInactivityTtlMinutes(15);
        executionGuard = new RoomGameTimerExecutionGuard(
                redisTemplate,
                timerProperties,
                lifecycleProperties
        );
    }

    @Test
    void executeMarksTheTimerCompletedAndReleasesItsOwnedLock() {
        RoomGameTimer timer = timer();
        given(redisTemplate.hasKey("rooms:game-timers:completed:25:3:HINT:1000"))
                .willReturn(false);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(
                eq("rooms:game-timers:execution-lock:25:3:HINT:1000"),
                anyString(),
                eq(Duration.ofSeconds(30))
        )).willReturn(true);

        ExecutionStatus status = executionGuard.execute(timer, action);

        assertThat(status).isEqualTo(ExecutionStatus.EXECUTED);
        verify(action).run();
        verify(valueOperations).set(
                "rooms:game-timers:completed:25:3:HINT:1000",
                "1",
                Duration.ofMinutes(15)
        );
        verify(redisTemplate).execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of("rooms:game-timers:execution-lock:25:3:HINT:1000")),
                anyString()
        );
    }

    @Test
    void executeSkipsAnAlreadyCompletedTimer() {
        RoomGameTimer timer = timer();
        given(redisTemplate.hasKey("rooms:game-timers:completed:25:3:HINT:1000"))
                .willReturn(true);

        ExecutionStatus status = executionGuard.execute(timer, action);

        assertThat(status).isEqualTo(ExecutionStatus.ALREADY_COMPLETED);
        verify(action, never()).run();
        verify(valueOperations, never()).setIfAbsent(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any(Duration.class)
        );
    }

    @Test
    void executeReportsBusyWhileAnotherServerOwnsTheLock() {
        RoomGameTimer timer = timer();
        given(redisTemplate.hasKey("rooms:game-timers:completed:25:3:HINT:1000"))
                .willReturn(false);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(
                eq("rooms:game-timers:execution-lock:25:3:HINT:1000"),
                anyString(),
                eq(Duration.ofSeconds(30))
        )).willReturn(false);

        ExecutionStatus status = executionGuard.execute(timer, action);

        assertThat(status).isEqualTo(ExecutionStatus.BUSY);
        verify(action, never()).run();
        verify(valueOperations, never()).set(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any(Duration.class)
        );
    }

    private RoomGameTimer timer() {
        return new RoomGameTimer(
                25L,
                3,
                RoomGameTimerType.HINT,
                Instant.ofEpochMilli(1_000L)
        );
    }
}
