package com.dogdog.nomat.global.config;

import com.dogdog.nomat.global.dto.WebSocketErrorResponse;
import com.dogdog.nomat.global.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class StompErrorHandler extends StompSubProtocolErrorHandler {

    private final ObjectMapper objectMapper;

    public StompErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable exception) {
        Throwable cause = unwrap(exception);
        String messageCode = messageCode(cause);
        logStompError(clientMessage, messageCode, cause);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setMessage(messageCode);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(payload(messageCode), accessor.getMessageHeaders());
    }

    private String messageCode(Throwable exception) {
        if (exception instanceof BusinessException businessException) {
            return businessException.getMessageCode();
        }
        if (exception instanceof BadCredentialsException) {
            return "invalid_token";
        }
        if (exception instanceof AccessDeniedException) {
            String message = exception.getMessage();
            return message == null || message.isBlank() ? "forbidden_room_access" : message;
        }

        return "internal_server_error";
    }

    private Throwable unwrap(Throwable exception) {
        Throwable current = exception;
        while (current instanceof MessagingException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private void logStompError(Message<byte[]> clientMessage, String messageCode, Throwable exception) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(clientMessage, StompHeaderAccessor.class);
        log.warn(
                "STOMP client message processing failed. messageCode={}, command={}, destination={}, sessionId={}",
                messageCode,
                accessor == null ? null : accessor.getCommand(),
                accessor == null ? null : accessor.getDestination(),
                accessor == null ? null : accessor.getSessionId(),
                exception
        );
    }

    private byte[] payload(String messageCode) {
        try {
            return objectMapper.writeValueAsBytes(WebSocketErrorResponse.of(messageCode));
        } catch (JacksonException exception) {
            return ("{\"message\":\"internal_server_error\",\"data\":null}")
                    .getBytes(StandardCharsets.UTF_8);
        }
    }
}
