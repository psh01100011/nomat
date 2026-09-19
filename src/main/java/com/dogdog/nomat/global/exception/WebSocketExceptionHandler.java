package com.dogdog.nomat.global.exception;

import com.dogdog.nomat.global.dto.WebSocketErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ControllerAdvice;

@ControllerAdvice
@Slf4j
public class WebSocketExceptionHandler {

    @MessageExceptionHandler(BusinessException.class)
    @SendToUser("/queue/errors")
    public WebSocketErrorResponse handleBusinessException(BusinessException exception) {
        log.debug("event=websocket_business_rejection code={}", exception.getMessageCode());
        return WebSocketErrorResponse.of(exception.getMessageCode(), exception.getData());
    }

    @MessageExceptionHandler(BadCredentialsException.class)
    @SendToUser("/queue/errors")
    public WebSocketErrorResponse handleBadCredentialsException() {
        log.debug("event=websocket_authentication_rejected");
        return WebSocketErrorResponse.of("invalid_token");
    }

    @MessageExceptionHandler(AccessDeniedException.class)
    @SendToUser("/queue/errors")
    public WebSocketErrorResponse handleAccessDeniedException(AccessDeniedException exception) {
        log.debug("event=websocket_access_rejected");
        return WebSocketErrorResponse.of(messageCode(exception, "forbidden_room_access"));
    }

    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    @SendToUser("/queue/errors")
    public WebSocketErrorResponse handleMethodArgumentNotValidException(MethodArgumentNotValidException exception) {
        log.debug("event=websocket_validation_rejection");
        return WebSocketErrorResponse.of("invalid_request");
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/errors")
    public WebSocketErrorResponse handleException(Exception exception) {
        log.error("event=unhandled_websocket_exception", exception);
        return WebSocketErrorResponse.of("internal_server_error");
    }

    private String messageCode(Exception exception, String fallback) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? fallback : message;
    }
}
