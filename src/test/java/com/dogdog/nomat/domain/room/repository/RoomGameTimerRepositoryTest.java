package com.dogdog.nomat.domain.room.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.dogdog.nomat.domain.room.config.RoomLifecycleProperties;
import com.dogdog.nomat.domain.room.model.ClaimedRoomGameTimer;
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
                .when(lifecycleProperties.timerTrackingTtl())
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
                eq(timerKeys(25L)),
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
                eq(timerKeys(25L)),
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
                eq(timerKeys(25L)),
                eq("25:3:HINT"),
                eq("2000")
        );
    }

    @Test
    void claimDueReturnsTimersWithTheSameLeaseOwner() {
        given(redisTemplate.execute(
                ArgumentMatchers.<RedisScript<List>>any(),
                eq(List.of(
                        "rooms:game-timers:due-at",
                        "rooms:game-timers:lease-until",
                        "rooms:game-timers:lease-owner",
                        "rooms:game-timers:lease-due-at"
                )),
                eq("3000"),
                eq("8000"),
                eq("100"),
                ArgumentMatchers.anyString()
        )).willReturn(List.of(
                "25:3:HINT", "1000",
                "26:4:QUESTION_TIMEOUT", "2000"
        ));

        List<ClaimedRoomGameTimer> timers = repository.claimDue(
                Instant.ofEpochMilli(3_000L),
                Duration.ofSeconds(5),
                100
        );

        assertThat(timers)
                .extracting(ClaimedRoomGameTimer::timer)
                .containsExactly(
                new RoomGameTimer(25L, 3, RoomGameTimerType.HINT, Instant.ofEpochMilli(1_000L)),
                new RoomGameTimer(26L, 4, RoomGameTimerType.QUESTION_TIMEOUT, Instant.ofEpochMilli(2_000L))
        );
        assertThat(timers)
                .extracting(ClaimedRoomGameTimer::ownerToken)
                .allSatisfy(ownerToken -> assertThat(ownerToken).isNotBlank())
                .containsOnly(timers.getFirst().ownerToken());
    }

    @Test
    void completeRemovesOnlyTheLeaseOwnedByTheClaim() {
        RoomGameTimer timer = new RoomGameTimer(
                25L, 3, RoomGameTimerType.HINT, Instant.ofEpochMilli(1_000L)
        );
        ClaimedRoomGameTimer claimedTimer = new ClaimedRoomGameTimer(timer, "owner-token");
        given(redisTemplate.execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of(
                        "rooms:game-timers:lease-until",
                        "rooms:game-timers:lease-owner",
                        "rooms:game-timers:lease-due-at",
                        "rooms:game-timers:room:25"
                )),
                eq("25:3:HINT"),
                eq("owner-token")
        )).willReturn(1L);

        assertThat(repository.complete(claimedTimer)).isTrue();
    }

    private List<String> timerKeys(Long roomId) {
        return List.of(
                "rooms:game-timers:due-at",
                "rooms:game-timers:lease-until",
                "rooms:game-timers:lease-owner",
                "rooms:game-timers:lease-due-at",
                "rooms:game-timers:room:" + roomId
        );
    }
}
