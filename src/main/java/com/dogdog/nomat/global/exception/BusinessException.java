package com.dogdog.nomat.global.exception;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String messageCode;
    private final Object data;

    public BusinessException(HttpStatus status, String messageCode) {
        this(status, messageCode, null);
    }

    public BusinessException(HttpStatus status, String messageCode, Object data) {
        super(messageCode);
        this.status = status;
        this.messageCode = messageCode;
        this.data = data;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessageCode() {
        return messageCode;
    }

    public Object getData() {
        return data;
    }
}
