package com.dogdog.nomat.domain.room.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.dogdog.nomat.domain.room.dto.RoomEventMessage;
import com.dogdog.nomat.domain.room.model.RoomDomainEvent;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RoomEventSubscriberTest {

    @Mock
    private Message message;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private SimpMessagingTemplate firstServerMessagingTemplate;

    @Mock
    private SimpMessagingTemplate secondServerMessagingTemplate;

    @Test
    void sameRedisEventIsDeliveredToSubscribersOnEachServer() throws Exception {
        byte[] payload = "serialized-event".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        RoomEventMessage event = RoomEventMessage.from(RoomDomainEvent.chatMessage(
                25L,
                3L,
                "tester3",
                "MEMBER",
                "안녕",
                "message-1",
                LocalDateTime.now()
        ));
        given(message.getBody()).willReturn(payload);
        given(objectMapper.readValue(payload, RoomEventMessage.class)).willReturn(event);
        RoomEventSubscriber firstServer = new RoomEventSubscriber(firstServerMessagingTemplate, objectMapper);
        RoomEventSubscriber secondServer = new RoomEventSubscriber(secondServerMessagingTemplate, objectMapper);

        firstServer.onMessage(message, null);
        secondServer.onMessage(message, null);

        verify(firstServerMessagingTemplate).convertAndSend("/topic/rooms/25", event);
        verify(secondServerMessagingTemplate).convertAndSend("/topic/rooms/25", event);
    }
}
