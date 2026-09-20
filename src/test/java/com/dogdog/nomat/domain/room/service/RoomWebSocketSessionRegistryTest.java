package com.dogdog.nomat.domain.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.dogdog.nomat.domain.room.config.RoomWebSocketProperties;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RoomWebSocketSessionRegistryTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private RoomWebSocketProperties webSocketProperties;

    private RoomWebSocketSessionRegistry sessionRegistry;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForSet()).willReturn(setOperations);
        sessionRegistry = new RoomWebSocketSessionRegistry(redisTemplate, webSocketProperties);
    }

    @Test
    void connectStoresSharedSessionLease() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(webSocketProperties.sessionTtl()).willReturn(Duration.ofMinutes(10));

        sessionRegistry.connect("session-b", 3L);

        verify(valueOperations).set("rooms:websocket:sessions:session-b", "3", Duration.ofMinutes(10));
        verify(setOperations).add("rooms:websocket:users:3:sessions", "session-b");
        verify(redisTemplate).expire("rooms:websocket:users:3:sessions", Duration.ofMinutes(10));
    }

    @Test
    void activeSessionOnAnotherServerPreventsAutomaticLeave() {
        given(setOperations.members("rooms:websocket:users:3:sessions")).willReturn(Set.of("session-b"));
        given(redisTemplate.hasKey("rooms:websocket:sessions:session-b")).willReturn(true);

        assertThat(sessionRegistry.hasActiveSession(3L)).isTrue();
    }

    @Test
    void expiredSessionIsRemovedFromUserSessionSet() {
        given(setOperations.members("rooms:websocket:users:3:sessions")).willReturn(Set.of("expired-session"));
        given(redisTemplate.hasKey("rooms:websocket:sessions:expired-session")).willReturn(false);

        assertThat(sessionRegistry.hasActiveSession(3L)).isFalse();
        verify(setOperations).remove("rooms:websocket:users:3:sessions", "expired-session");
    }
}
