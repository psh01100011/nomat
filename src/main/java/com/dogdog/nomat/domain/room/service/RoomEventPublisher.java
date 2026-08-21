package com.dogdog.nomat.domain.room.service;

import com.dogdog.nomat.domain.room.dto.RoomEventMessage;
import com.dogdog.nomat.domain.room.model.RoomDomainEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoomEventPublisher {

    private static final String ROOM_TOPIC_PREFIX = "/topic/rooms/";

    private final SimpMessagingTemplate messagingTemplate;

    public void publish(List<RoomDomainEvent> events) {
        events.forEach(this::publish);
    }

    private void publish(RoomDomainEvent event) {
        messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + event.roomId(), RoomEventMessage.from(event));
    }
}
