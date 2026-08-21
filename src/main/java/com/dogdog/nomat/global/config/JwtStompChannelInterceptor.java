package com.dogdog.nomat.global.config;

import com.dogdog.nomat.domain.room.service.RoomWebSocketSessionRegistry;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class JwtStompChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final RoomWebSocketSessionRegistry sessionRegistry;

    public JwtStompChannelInterceptor(
            @Qualifier("jwtDecoder") JwtDecoder jwtDecoder,
            RoomWebSocketSessionRegistry sessionRegistry
    ) {
        this.jwtDecoder = jwtDecoder;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !requiresAuthentication(accessor.getCommand())) {
            return message;
        }

        if (accessor.getUser() != null) {
            return message;
        }

        Long userId = decodeUserId(accessor);
        accessor.setUser(new StompUserPrincipal(userId));
        if (accessor.getCommand() == StompCommand.CONNECT) {
            sessionRegistry.connect(accessor.getSessionId(), userId);
        }
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

    private String firstNativeHeader(StompHeaderAccessor accessor, String name) {
        List<String> values = accessor.getNativeHeader(name);
        if (values == null || values.isEmpty()) {
            return null;
        }

        return values.getFirst();
    }
}
