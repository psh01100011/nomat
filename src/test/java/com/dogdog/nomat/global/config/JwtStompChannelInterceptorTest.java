package com.dogdog.nomat.global.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.dogdog.nomat.domain.game.entity.TimeLimitMode;
import com.dogdog.nomat.domain.room.model.RoomMember;
import com.dogdog.nomat.domain.room.model.RoomState;
import com.dogdog.nomat.domain.room.repository.RoomRedisRepository;
import com.dogdog.nomat.domain.room.service.RoomWebSocketSessionRegistry;
import com.dogdog.nomat.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

@ExtendWith(MockitoExtension.class)
class JwtStompChannelInterceptorTest {

    @Mock
    private JwtDecoder jwtDecoder;

    @Mock
    private RoomRedisRepository roomRedisRepository;

    private final RoomWebSocketSessionRegistry sessionRegistry = new RoomWebSocketSessionRegistry();

    @Test
    void subscribeRoomTopicAllowsRoomMember() {
        JwtStompChannelInterceptor interceptor = interceptor();
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(room(25L, 3L)));

        interceptor.preSend(subscribeMessage(3L, "/topic/rooms/25"), null);
    }

    @Test
    void subscribeRoomTopicRejectsNonMemberOrUnknownRoom() {
        JwtStompChannelInterceptor interceptor = interceptor();
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(room(25L, 3L)));

        assertThatThrownBy(() -> interceptor.preSend(subscribeMessage(4L, "/topic/rooms/25"), null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("forbidden_room_access");
    }

    @Test
    void subscribeRoomTopicRejectsUnknownRoom() {
        JwtStompChannelInterceptor interceptor = interceptor();

        given(roomRedisRepository.findById(26L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> interceptor.preSend(subscribeMessage(4L, "/topic/rooms/26"), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("room_not_found");
    }

    private JwtStompChannelInterceptor interceptor() {
        return new JwtStompChannelInterceptor(jwtDecoder, sessionRegistry, roomRedisRepository);
    }

    private Message<byte[]> subscribeMessage(Long userId, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("session-" + userId);
        accessor.setDestination(destination);
        accessor.setUser(new StompUserPrincipal(userId));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private RoomState room(Long roomId, Long userId) {
        return RoomState.waiting(
                roomId,
                "방",
                15L,
                "맵",
                null,
                7L,
                "음악",
                10,
                1,
                false,
                null,
                10,
                5,
                30,
                TimeLimitMode.FIXED,
                true,
                true,
                10,
                new RoomMember(userId, "tester" + userId, null, true, LocalDateTime.now()),
                LocalDateTime.now()
        );
    }
}
