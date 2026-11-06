package com.dogdog.nomat.domain.room.service;

import com.dogdog.nomat.domain.room.dto.RoomEventMessage;
import com.dogdog.nomat.domain.room.monitoring.RoomMonitoringMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class RoomEventSubscriber implements MessageListener {

    private static final String ROOM_TOPIC_PREFIX = "/topic/rooms/";

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final RoomMonitoringMetrics metrics;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        RoomEventMessage event = null;
        try {
            event = objectMapper.readValue(message.getBody(), RoomEventMessage.class);
            messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + event.roomId(), event);
            metrics.recordEventDelivered(event.type(), "success");
        } catch (JacksonException exception) {
            metrics.recordEventDelivered(null, "deserialization_failed");
            log.error("event=room_event_deserialization_failed payloadSize={}", message.getBody().length, exception);
        } catch (RuntimeException exception) {
            metrics.recordEventDelivered(event == null ? null : event.type(), "delivery_failed");
            log.error("event=room_event_local_delivery_failed", exception);
        }
    }
}
