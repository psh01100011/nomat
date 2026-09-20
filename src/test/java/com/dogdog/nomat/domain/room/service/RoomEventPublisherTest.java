package com.dogdog.nomat.domain.room.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.dogdog.nomat.domain.room.config.RoomEventProperties;
import com.dogdog.nomat.domain.room.dto.RoomEventMessage;
import com.dogdog.nomat.domain.room.model.RoomDomainEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RoomEventPublisherTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private RoomEventProperties eventProperties;

    @Test
    void publishSendsSerializedEventToSharedRedisChannel() throws Exception {
        RoomDomainEvent event = RoomDomainEvent.chatMessage(
                25L,
                3L,
                "tester3",
                "MEMBER",
                "안녕",
                "message-1",
                LocalDateTime.now()
        );
        given(objectMapper.writeValueAsString(any(RoomEventMessage.class))).willReturn("serialized-event");
        given(eventProperties.getChannel()).willReturn("nomat:rooms:events");
        RoomEventPublisher publisher = new RoomEventPublisher(redisTemplate, objectMapper, eventProperties);

        publisher.publish(List.of(event));

        verify(redisTemplate).convertAndSend("nomat:rooms:events", "serialized-event");
    }
}
