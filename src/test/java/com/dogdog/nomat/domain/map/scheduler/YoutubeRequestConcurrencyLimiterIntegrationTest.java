package com.dogdog.nomat.domain.map.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.dogdog.nomat.domain.map.config.AudioProcessingProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class YoutubeRequestConcurrencyLimiterIntegrationTest {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4"))
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofSeconds(30));

    @Test
    void sharesTheLimitBetweenServersAndRecoversExpiredPermits() throws Exception {
        RedisClient firstServer = redisClient();
        RedisClient secondServer = redisClient();

        try {
            YoutubeRequestConcurrencyLimiter.Permit first = firstServer.limiter().tryAcquire().orElseThrow();
            YoutubeRequestConcurrencyLimiter.Permit abandoned = firstServer.limiter().tryAcquire().orElseThrow();
            assertThat(secondServer.limiter().tryAcquire()).isEmpty();

            first.close();
            YoutubeRequestConcurrencyLimiter.Permit replacement = secondServer.limiter().tryAcquire().orElseThrow();
            assertThat(firstServer.limiter().tryAcquire()).isEmpty();

            Thread.sleep(1_100);

            YoutubeRequestConcurrencyLimiter.Permit recovered = secondServer.limiter()
                    .tryAcquire()
                    .orElseThrow();
            recovered.close();
            replacement.close();
            abandoned.close();
        } finally {
            flushDatabase(secondServer.redisTemplate());
            firstServer.close();
            secondServer.close();
        }
    }

    private RedisClient redisClient() {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
                REDIS.getHost(),
                REDIS.getMappedPort(6379)
        );
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        AudioProcessingProperties properties = new AudioProcessingProperties();
        properties.setYoutubeMaxConcurrency(2);
        properties.setYoutubePermitLeaseSeconds(1);
        return new RedisClient(
                connectionFactory,
                redisTemplate,
                new YoutubeRequestConcurrencyLimiter(redisTemplate, properties)
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
            YoutubeRequestConcurrencyLimiter limiter
    ) implements AutoCloseable {

        @Override
        public void close() {
            connectionFactory.destroy();
        }
    }
}
