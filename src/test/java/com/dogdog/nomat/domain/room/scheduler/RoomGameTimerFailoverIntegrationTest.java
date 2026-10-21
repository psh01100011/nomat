package com.dogdog.nomat.domain.room.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.dogdog.nomat.domain.room.config.RoomGameTimerProperties;
import com.dogdog.nomat.domain.room.config.RoomLifecycleProperties;
import com.dogdog.nomat.domain.room.model.ClaimedRoomGameTimer;
import com.dogdog.nomat.domain.room.model.RoomGameTimer;
import com.dogdog.nomat.domain.room.model.RoomGameTimerType;
import com.dogdog.nomat.domain.room.monitoring.RoomMonitoringMetrics;
import com.dogdog.nomat.domain.room.repository.RoomGameTimerRepository;
import com.dogdog.nomat.domain.room.service.RoomGameProgressService;
import com.dogdog.nomat.domain.room.service.RoomGameTimerExecutionGuard;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class RoomGameTimerFailoverIntegrationTest {

    private static final Duration TEST_LEASE_DURATION = Duration.ofMillis(200);

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4"))
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofSeconds(30));

    @Test
    void anotherServerTakesOverExpiredTimersAndRestartedServerDoesNotRepeatThem() throws Exception {
        RoomLifecycleProperties lifecycleProperties = lifecycleProperties();
        RoomGameTimerProperties timerProperties = timerProperties();
        RedisClient firstServer = redisClient(lifecycleProperties);
        RedisClient secondServer = redisClient(lifecycleProperties);
        List<RoomGameTimer> timers = timers();

        try {
            timers.forEach(firstServer.repository()::schedule);
            List<ClaimedRoomGameTimer> abandonedClaims = firstServer.repository().claimDue(
                    Instant.now(),
                    TEST_LEASE_DURATION,
                    10
            );
            assertThat(abandonedClaims)
                    .extracting(ClaimedRoomGameTimer::timer)
                    .containsExactlyInAnyOrderElementsOf(timers);

            firstServer.close();

            List<RoomGameTimer> executedTimers = new CopyOnWriteArrayList<>();
            RoomGameProgressService progressService = mock(RoomGameProgressService.class);
            doAnswer(invocation -> {
                executedTimers.add(invocation.getArgument(0, RoomGameTimer.class));
                return null;
            }).when(progressService).executeTimer(any(RoomGameTimer.class));
            RoomGameTimerScheduler secondScheduler = new RoomGameTimerScheduler(
                    secondServer.repository(),
                    progressService,
                    new RoomGameTimerExecutionGuard(
                            secondServer.redisTemplate(),
                            timerProperties,
                            lifecycleProperties
                    ),
                    timerProperties,
                    mock(RoomMonitoringMetrics.class)
            );

            awaitTimerExecution(secondScheduler, executedTimers, timers.size());
            assertThat(executedTimers).containsExactlyInAnyOrderElementsOf(timers);

            try (RedisClient restartedServer = redisClient(lifecycleProperties)) {
                assertThat(restartedServer.repository().claimDue(
                        Instant.now(),
                        TEST_LEASE_DURATION,
                        10
                )).isEmpty();
            }
        } finally {
            flushDatabase(secondServer.redisTemplate());
            firstServer.close();
            secondServer.close();
        }
    }

    private void awaitTimerExecution(
            RoomGameTimerScheduler scheduler,
            List<RoomGameTimer> executedTimers,
            int expectedCount
    ) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (executedTimers.size() < expectedCount && System.nanoTime() < deadline) {
            scheduler.processDueTimers();
            Thread.sleep(25);
        }
    }

    private List<RoomGameTimer> timers() {
        Instant dueAt = Instant.ofEpochMilli(Instant.now().minusSeconds(1).toEpochMilli());
        return List.of(
                new RoomGameTimer(25L, 0, RoomGameTimerType.HINT, dueAt),
                new RoomGameTimer(25L, 0, RoomGameTimerType.QUESTION_TIMEOUT, dueAt),
                new RoomGameTimer(25L, 0, RoomGameTimerType.NEXT_QUESTION, dueAt)
        );
    }

    private RoomLifecycleProperties lifecycleProperties() {
        RoomLifecycleProperties properties = new RoomLifecycleProperties();
        properties.setInactivityTtlMinutes(15);
        return properties;
    }

    private RoomGameTimerProperties timerProperties() {
        RoomGameTimerProperties properties = new RoomGameTimerProperties();
        properties.setBatchSize(10);
        properties.setLeaseDurationMs(TEST_LEASE_DURATION.toMillis());
        properties.setExecutionLockDurationMs(2_000);
        return properties;
    }

    private RedisClient redisClient(RoomLifecycleProperties lifecycleProperties) {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
                REDIS.getHost(),
                REDIS.getMappedPort(6379)
        );
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        return new RedisClient(
                connectionFactory,
                redisTemplate,
                new RoomGameTimerRepository(redisTemplate, lifecycleProperties)
        );
    }

    private void flushDatabase(StringRedisTemplate redisTemplate) {
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    private record RedisClient(
            LettuceConnectionFactory connectionFactory,
            StringRedisTemplate redisTemplate,
            RoomGameTimerRepository repository
    ) implements AutoCloseable {

        @Override
        public void close() {
            connectionFactory.destroy();
        }
    }
}
