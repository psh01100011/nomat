package com.dogdog.nomat.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.dogdog.nomat.global.dto.WebSocketErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

class WebSocketExceptionHandlerTest {

    private final WebSocketExceptionHandler exceptionHandler = new WebSocketExceptionHandler();

    @Test
    void handleBusinessExceptionReturnsApiResponseShape() {
        WebSocketErrorResponse response = exceptionHandler.handleBusinessException(
                new BusinessException(HttpStatus.NOT_FOUND, "room_not_found")
        );

        assertThat(response.message()).isEqualTo("room_not_found");
        assertThat(response.data()).isNull();
    }

    @Test
    void handleBadCredentialsExceptionReturnsInvalidToken() {
        WebSocketErrorResponse response = exceptionHandler.handleBadCredentialsException();

        assertThat(response.message()).isEqualTo("invalid_token");
        assertThat(response.data()).isNull();
    }

    @Test
    void handleAccessDeniedExceptionReturnsForbiddenRoomAccess() {
        WebSocketErrorResponse response = exceptionHandler.handleAccessDeniedException(
                new AccessDeniedException("forbidden_room_access")
        );

        assertThat(response.message()).isEqualTo("forbidden_room_access");
        assertThat(response.data()).isNull();
    }
}
