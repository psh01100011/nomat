package com.dogdog.nomat.global.dto;

public record WebSocketErrorResponse(
        String message,
        Object data
) {

    public static WebSocketErrorResponse of(String message, Object data) {
        return new WebSocketErrorResponse(message, data);
    }

    public static WebSocketErrorResponse of(String message) {
        return of(message, null);
    }
}
