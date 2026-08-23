package com.dogdog.nomat.global.exception;

import com.dogdog.nomat.global.dto.WebSocketErrorResponse;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ControllerAdvice;

@ControllerAdvice
public class WebSocketExceptionHandler {

    @MessageExceptionHandler(BusinessException.class)
    @SendToUser("/queue/errors")
    public WebSocketErrorResponse handleBusinessException(BusinessException exception) {
        return WebSocketErrorResponse.of(exception.getMessageCode(), exception.getData());
    }

    @MessageExceptionHandler(BadCredentialsException.class)
    @SendToUser("/queue/errors")
    public WebSocketErrorResponse handleBadCredentialsException() {
        return WebSocketErrorResponse.of("invalid_token");
    }

    @MessageExceptionHandler(AccessDeniedException.class)
    @SendToUser("/queue/errors")
    public WebSocketErrorResponse handleAccessDeniedException(AccessDeniedException exception) {
        return WebSocketErrorResponse.of(messageCode(exception, "forbidden_room_access"));
    }

    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    @SendToUser("/queue/errors")
    public WebSocketErrorResponse handleMethodArgumentNotValidException() {
        return WebSocketErrorResponse.of("invalid_request");
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/errors")
    public WebSocketErrorResponse handleException() {
        return WebSocketErrorResponse.of("internal_server_error");
    }

    private String messageCode(Exception exception, String fallback) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? fallback : message;
    }
}
