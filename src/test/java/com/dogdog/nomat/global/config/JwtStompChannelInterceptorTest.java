package com.dogdog.nomat.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.dogdog.nomat.domain.auth.model.AuthenticatedUserType;
import com.dogdog.nomat.domain.game.entity.TimeLimitMode;
import com.dogdog.nomat.domain.room.model.RoomMember;
import com.dogdog.nomat.domain.room.model.RoomState;
import com.dogdog.nomat.domain.room.repository.RoomRedisRepository;
import com.dogdog.nomat.domain.room.service.RoomWebSocketSessionRegistry;
import com.dogdog.nomat.global.exception.BusinessException;
import java.security.Principal;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
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
    void connectAuthenticatesGuestTokenAndRegistersSession() {
        JwtStompChannelInterceptor interceptor = interceptor();
        given(jwtDecoder.decode("access-token")).willReturn(guestJwt(-1L));

        interceptor.preSend(connectMessage("session-guest", null), null);

        assertThat(sessionRegistry.hasActiveSession(-1L)).isTrue();
    }

    @Test
    void connectIgnoresNonStompPrincipalAndAuthenticatesToken() {
        JwtStompChannelInterceptor interceptor = interceptor();
        given(jwtDecoder.decode("access-token")).willReturn(jwt(3L));

        interceptor.preSend(connectMessage("session-3", () -> "authenticated-user"), null);

        assertThat(sessionRegistry.hasActiveSession(3L)).isTrue();
    }

    @Test
    void connectRejectsMissingSessionIdWithExplicitMessageCode() {
        JwtStompChannelInterceptor interceptor = interceptor();
        given(jwtDecoder.decode("access-token")).willReturn(jwt(3L));

        assertThatThrownBy(() -> interceptor.preSend(connectMessage(null, null), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("websocket_session_unavailable");

        assertThat(sessionRegistry.hasActiveSession(3L)).isFalse();
    }

    @Test
    void subscribeRoomTopicAllowsGuestRoomMember() {
        JwtStompChannelInterceptor interceptor = interceptor();
        given(roomRedisRepository.findById(25L)).willReturn(Optional.of(room(25L, -1L)));

        interceptor.preSend(subscribeMessage(-1L, "/topic/rooms/25"), null);
    }

    @Test
    void sendAllowsAuthenticatedGuestPrincipal() {
        JwtStompChannelInterceptor interceptor = interceptor();

        interceptor.preSend(sendMessage(-1L), null);
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

    private Message<byte[]> connectMessage(String sessionId, Principal principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId(sessionId);
        accessor.setUser(principal);
        accessor.setNativeHeader("Authorization", "Bearer access-token");
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> subscribeMessage(Long userId, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("session-" + userId);
        accessor.setDestination(destination);
        accessor.setUser(new StompUserPrincipal(userId));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> sendMessage(Long userId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setSessionId("session-" + userId);
        accessor.setDestination("/app/rooms/25/chat");
        accessor.setUser(new StompUserPrincipal(userId));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Jwt jwt(Long userId) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("access-token")
                .header("alg", "none")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .claim("userId", userId)
                .build();
    }

    private Jwt guestJwt(Long userId) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("access-token")
                .header("alg", "none")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .claim("userId", userId)
                .claim("userType", AuthenticatedUserType.GUEST.name())
                .claim("nickname", "손님")
                .build();
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
