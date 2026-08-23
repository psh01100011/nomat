package com.dogdog.nomat.global.config;

import com.dogdog.nomat.domain.room.repository.RoomRedisRepository;
import com.dogdog.nomat.domain.room.service.RoomWebSocketSessionRegistry;
import com.dogdog.nomat.global.exception.BusinessException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

@Component
public class JwtStompChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern ROOM_TOPIC_PATTERN = Pattern.compile("^/topic/rooms/(\\d+)$");

    private final JwtDecoder jwtDecoder;
    private final RoomWebSocketSessionRegistry sessionRegistry;
    private final RoomRedisRepository roomRedisRepository;

    public JwtStompChannelInterceptor(
            @Qualifier("jwtDecoder") JwtDecoder jwtDecoder,
            RoomWebSocketSessionRegistry sessionRegistry,
            RoomRedisRepository roomRedisRepository
    ) {
        this.jwtDecoder = jwtDecoder;
        this.sessionRegistry = sessionRegistry;
        this.roomRedisRepository = roomRedisRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !requiresAuthentication(accessor.getCommand())) {
            return message;
        }

        if (accessor.getUser() != null) {
            authorizeSubscription(accessor, Long.valueOf(accessor.getUser().getName()));
            return message;
        }

        Long userId = decodeUserId(accessor);
        accessor.setUser(new StompUserPrincipal(userId));
        if (accessor.getCommand() == StompCommand.CONNECT) {
            sessionRegistry.connect(accessor.getSessionId(), userId);
        }
        authorizeSubscription(accessor, userId);
        return message;
    }

    private boolean requiresAuthentication(StompCommand command) {
        return command == StompCommand.CONNECT
                || command == StompCommand.SUBSCRIBE
                || command == StompCommand.SEND;
    }

    private Long decodeUserId(StompHeaderAccessor accessor) {
        String authorization = firstNativeHeader(accessor, "Authorization");
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BadCredentialsException("invalid_token");
        }

        try {
            Jwt jwt = jwtDecoder.decode(authorization.substring(BEARER_PREFIX.length()));
            Number userId = jwt.getClaim("userId");
            if (userId == null) {
                throw new BadCredentialsException("invalid_token");
            }
            return userId.longValue();
        } catch (JwtException exception) {
            throw new BadCredentialsException("invalid_token", exception);
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor, Long userId) {
        if (accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return;
        }

        Optional<Long> roomId = roomIdFromTopic(accessor.getDestination());
        if (roomId.isEmpty()) {
            return;
        }

        boolean member = roomRedisRepository.findById(roomId.get())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "room_not_found"))
                .hasMember(userId);
        if (!member) {
            throw new AccessDeniedException("forbidden_room_access");
        }
    }

    private Optional<Long> roomIdFromTopic(String destination) {
        if (!StringUtils.hasText(destination)) {
            return Optional.empty();
        }

        Matcher matcher = ROOM_TOPIC_PATTERN.matcher(destination);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        return Optional.of(Long.valueOf(matcher.group(1)));
    }

    private String firstNativeHeader(StompHeaderAccessor accessor, String name) {
        List<String> values = accessor.getNativeHeader(name);
        if (values == null || values.isEmpty()) {
            return null;
        }

        return values.getFirst();
    }
}
