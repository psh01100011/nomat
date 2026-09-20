package com.dogdog.nomat.domain.room.service;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
@Slf4j
public class RoomWebSocketDisconnectListener {

    private static final Duration AUTO_LEAVE_DELAY = Duration.ofSeconds(60);

    private final RoomWebSocketSessionRegistry sessionRegistry;
    private final RoomService roomService;
    private final TaskScheduler taskScheduler;

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        Long userId = sessionRegistry.disconnect(event.getSessionId());
        if (userId == null) {
            return;
        }

        log.debug("event=websocket_disconnect_detected userId={} autoLeaveDelaySeconds={}", userId, AUTO_LEAVE_DELAY.toSeconds());

        taskScheduler.schedule(
                () -> leaveIfStillDisconnected(userId),
                Instant.now().plus(AUTO_LEAVE_DELAY)
        );
    }

    private void leaveIfStillDisconnected(Long userId) {
        if (!sessionRegistry.hasActiveSession(userId)) {
            roomService.leaveCurrentRoomByDisconnect(userId);
        }
    }
}
