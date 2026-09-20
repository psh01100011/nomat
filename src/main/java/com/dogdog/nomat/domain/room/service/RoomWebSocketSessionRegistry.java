package com.dogdog.nomat.domain.room.service;

import com.dogdog.nomat.domain.room.config.RoomWebSocketProperties;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoomWebSocketSessionRegistry {

    private static final String SESSION_KEY_PREFIX = "rooms:websocket:sessions:";
    private static final String USER_SESSIONS_KEY_PREFIX = "rooms:websocket:users:";

    private final Map<String, Long> userIdsBySessionId = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;
    private final RoomWebSocketProperties webSocketProperties;

    public void connect(String sessionId, Long userId) {
        refresh(sessionId, userId);
        userIdsBySessionId.put(sessionId, userId);
    }

    public void refresh(String sessionId, Long userId) {
        redisTemplate.opsForValue().set(
                sessionKey(sessionId),
                String.valueOf(userId),
                webSocketProperties.sessionTtl()
        );
        redisTemplate.opsForSet().add(userSessionsKey(userId), sessionId);
        redisTemplate.expire(userSessionsKey(userId), webSocketProperties.sessionTtl());
    }

    public Long disconnect(String sessionId) {
        Long userId = userIdsBySessionId.remove(sessionId);
        if (userId == null) {
            return null;
        }

        redisTemplate.delete(sessionKey(sessionId));
        redisTemplate.opsForSet().remove(userSessionsKey(userId), sessionId);
        return userId;
    }

    public boolean hasActiveSession(Long userId) {
        Set<String> sessionIds = redisTemplate.opsForSet().members(userSessionsKey(userId));
        if (sessionIds == null || sessionIds.isEmpty()) {
            return false;
        }

        boolean activeSessionExists = false;
        for (String sessionId : sessionIds) {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(sessionKey(sessionId)))) {
                activeSessionExists = true;
            } else {
                redisTemplate.opsForSet().remove(userSessionsKey(userId), sessionId);
            }
        }
        return activeSessionExists;
    }

    private String sessionKey(String sessionId) {
        return SESSION_KEY_PREFIX + sessionId;
    }

    private String userSessionsKey(Long userId) {
        return USER_SESSIONS_KEY_PREFIX + userId + ":sessions";
    }
}
