package com.dogdog.nomat.domain.room.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.dogdog.nomat.domain.room.config.RoomLifecycleProperties;
import com.dogdog.nomat.domain.room.model.RoomGameQuestion;
import com.dogdog.nomat.domain.room.model.RoomGameState;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers(disabledWithoutDocker = true)
class RoomGameStateTransitionIntegrationTest {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4"))
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofSeconds(30));

    @Test
    void onlyOneQuestionResolutionWinsWhenAllCausesArriveTogether() throws Exception {
        RoomLifecycleProperties lifecycleProperties = new RoomLifecycleProperties();
        lifecycleProperties.setInactivityTtlMinutes(15);
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        List<RedisClient> servers = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(4);

        try {
            for (int index = 0; index < 4; index++) {
                servers.add(redisClient(objectMapper, lifecycleProperties));
            }
            RoomGameState initialState = gameState();
            servers.getFirst().redisTemplate().opsForValue().set(
                    "rooms:games:25",
                    objectMapper.writeValueAsString(initialState),
                    Duration.ofMinutes(15)
            );

            CountDownLatch ready = new CountDownLatch(4);
            CountDownLatch start = new CountDownLatch(1);
            List<Callable<Optional<RoomGameState>>> transitions = List.of(
                    () -> servers.get(0).repository().tryRecordCorrectAnswer(
                            25L, 0, "정답", 3L, "정답", 1
                    ),
                    () -> servers.get(1).repository().tryVoteToSkip(
                            25L, 0, 1L, 4L, 1, LocalDateTime.now()
                    ),
                    () -> servers.get(2).repository().tryEndQuestionByTimeout(25L, 0),
                    () -> servers.get(3).repository().tryEndQuestionForAudioLoadFailure(25L, 0, 1L)
            );
            List<Future<Optional<RoomGameState>>> results = transitions.stream()
                    .map(transition -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return transition.call();
                    }))
                    .toList();

            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            long winnerCount = 0;
            for (Future<Optional<RoomGameState>> result : results) {
                if (result.get(5, TimeUnit.SECONDS).isPresent()) {
                    winnerCount++;
                }
            }
            assertThat(winnerCount).isEqualTo(1);

            RoomGameState finalState = servers.getFirst().repository().findGameState(25L).orElseThrow();
            assertThat(finalState.hasCurrentQuestionEnded()).isTrue();
            assertThat(finalState.questionOutcomes()).hasSize(1);
            assertThat(finalState.questionOutcomes().get(1).endedReason())
                    .isIn("CORRECT_ANSWER", "SKIPPED", "TIME_OVER", "AUDIO_LOAD_FAILED");
        } finally {
            executor.shutdownNow();
            if (!servers.isEmpty()) {
                flushDatabase(servers.getFirst().redisTemplate());
            }
            servers.forEach(RedisClient::close);
        }
    }

    private RoomGameState gameState() {
        LocalDateTime now = LocalDateTime.now();
        RoomGameQuestion question = new RoomGameQuestion(
                1L,
                1,
                "문제",
                List.of("정답"),
                "정답",
                "https://cdn.example.com/audio.mp3",
                "YOUTUBE",
                0,
                30_000,
                30_000
        );
        return RoomGameState.started(25L, "seed", List.of(question), Map.of(3L, 0, 4L, 0), now)
                .withStartedQuestion(0, now, 30);
    }

    private RedisClient redisClient(
            ObjectMapper objectMapper,
            RoomLifecycleProperties lifecycleProperties
    ) {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
                REDIS.getHost(),
                REDIS.getMappedPort(6379)
        );
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        RoomGameTimerRepository timerRepository = new RoomGameTimerRepository(
                redisTemplate,
                lifecycleProperties
        );
        return new RedisClient(
                connectionFactory,
                redisTemplate,
                new RoomRedisRepository(
                        redisTemplate,
                        objectMapper,
                        lifecycleProperties,
                        timerRepository
                )
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
            RoomRedisRepository repository
    ) {

        private void close() {
            connectionFactory.destroy();
        }
    }
}
