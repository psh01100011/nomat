package com.dogdog.nomat.domain.room.service;

import com.dogdog.nomat.domain.room.config.RoomEventProperties;
import com.dogdog.nomat.domain.room.dto.RoomEventMessage;
import com.dogdog.nomat.domain.room.model.RoomDomainEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class RoomEventPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RoomEventProperties eventProperties;

    public void publish(List<RoomDomainEvent> events) {
        events.forEach(this::publish);
    }

    private void publish(RoomDomainEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(RoomEventMessage.from(event));
            redisTemplate.convertAndSend(eventProperties.getChannel(), payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialize room event.", exception);
        }
    }
}
