package com.dogdog.nomat.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.dogdog.nomat.global.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.json.JsonMapper;

class StompErrorHandlerTest {

    private final StompErrorHandler errorHandler = new StompErrorHandler(new JsonMapper());

    @Test
    void handleClientMessageProcessingErrorReturnsInvalidTokenPayload() {
        Message<byte[]> errorMessage = errorHandler.handleClientMessageProcessingError(
                clientMessage(),
                new BadCredentialsException("invalid_token")
        );

        assertThat(payload(errorMessage)).isEqualTo("{\"message\":\"invalid_token\",\"data\":null}");
    }

    @Test
    void handleClientMessageProcessingErrorReturnsRoomNotFoundPayload() {
        Message<byte[]> errorMessage = errorHandler.handleClientMessageProcessingError(
                clientMessage(),
                new BusinessException(HttpStatus.NOT_FOUND, "room_not_found")
        );

        assertThat(payload(errorMessage)).isEqualTo("{\"message\":\"room_not_found\",\"data\":null}");
    }

    @Test
    void handleClientMessageProcessingErrorReturnsForbiddenPayload() {
        Message<byte[]> errorMessage = errorHandler.handleClientMessageProcessingError(
                clientMessage(),
                new AccessDeniedException("forbidden_room_access")
        );

        assertThat(payload(errorMessage)).isEqualTo("{\"message\":\"forbidden_room_access\",\"data\":null}");
    }

    private Message<byte[]> clientMessage() {
        return MessageBuilder.withPayload(new byte[0]).build();
    }

    private String payload(Message<byte[]> message) {
        return new String(message.getPayload(), StandardCharsets.UTF_8);
    }
}
