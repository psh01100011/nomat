package com.dogdog.nomat.domain.room.service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class RoomWebSocketSessionRegistry {

    private final Map<String, Long> userIdsBySessionId = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> sessionIdsByUserId = new ConcurrentHashMap<>();

    public void connect(String sessionId, Long userId) {
        userIdsBySessionId.put(sessionId, userId);
        sessionIdsByUserId.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(sessionId);
    }

    public Long disconnect(String sessionId) {
        Long userId = userIdsBySessionId.remove(sessionId);
        if (userId == null) {
            return null;
        }

        Set<String> sessionIds = sessionIdsByUserId.get(userId);
        if (sessionIds != null) {
            sessionIds.remove(sessionId);
            if (sessionIds.isEmpty()) {
                sessionIdsByUserId.remove(userId);
            }
        }
        return userId;
    }

    public boolean hasActiveSession(Long userId) {
        Set<String> sessionIds = sessionIdsByUserId.get(userId);
        return sessionIds != null && !sessionIds.isEmpty();
    }
}
