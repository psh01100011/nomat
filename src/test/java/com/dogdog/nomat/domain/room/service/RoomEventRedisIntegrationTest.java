package com.dogdog.nomat.domain.room.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dogdog.nomat.domain.room.config.RoomWebSocketProperties;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class RoomEventRedisIntegrationTest {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4"))
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofSeconds(30));

    @Test
    void publishedEventIsReceivedByTwoIndependentServerListeners() throws Exception {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
                REDIS.getHost(),
                REDIS.getMappedPort(6379)
        );
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        String channel = "nomat:test:rooms:events:" + UUID.randomUUID();
        CountDownLatch firstServerReceived = new CountDownLatch(1);
        CountDownLatch secondServerReceived = new CountDownLatch(1);
        RedisMessageListenerContainer firstServer = listenerContainer(
                connectionFactory,
                channel,
                firstServerReceived
        );
        RedisMessageListenerContainer secondServer = listenerContainer(
                connectionFactory,
                channel,
                secondServerReceived
        );

        try {
            firstServer.start();
            secondServer.start();
            for (int attempt = 0; attempt < 20
                    && (firstServerReceived.getCount() > 0 || secondServerReceived.getCount() > 0); attempt++) {
                redisTemplate.convertAndSend(channel, "room-event");
                Thread.sleep(100);
            }

            assertThat(firstServerReceived.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(secondServerReceived.await(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            firstServer.stop();
            secondServer.stop();
            connectionFactory.destroy();
        }
    }

    @Test
    void reconnectingOnAnotherServerKeepsUserSessionActive() {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
                REDIS.getHost(),
                REDIS.getMappedPort(6379)
        );
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        RoomWebSocketProperties properties = new RoomWebSocketProperties();
        RoomWebSocketSessionRegistry firstServer = new RoomWebSocketSessionRegistry(redisTemplate, properties);
        RoomWebSocketSessionRegistry secondServer = new RoomWebSocketSessionRegistry(redisTemplate, properties);

        try {
            firstServer.connect("first-server-session", 3L);
            secondServer.connect("second-server-session", 3L);
            firstServer.disconnect("first-server-session");

            assertThat(firstServer.hasActiveSession(3L)).isTrue();

            secondServer.disconnect("second-server-session");
            assertThat(firstServer.hasActiveSession(3L)).isFalse();
        } finally {
            connectionFactory.destroy();
        }
    }

    private RedisMessageListenerContainer listenerContainer(
            LettuceConnectionFactory connectionFactory,
            String channel,
            CountDownLatch received
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) -> received.countDown(), new ChannelTopic(channel));
        container.afterPropertiesSet();
        return container;
    }
}
