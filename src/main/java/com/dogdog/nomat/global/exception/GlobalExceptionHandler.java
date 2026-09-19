package com.dogdog.nomat.global.exception;

import com.dogdog.nomat.global.dto.ApiResponse;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final Pattern MESSAGE_CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*$");

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException exception) {
        if (exception.getStatus().is5xxServerError()) {
            log.warn(
                    "event=http_business_error status={} code={}",
                    exception.getStatus().value(),
                    exception.getMessageCode()
            );
        } else {
            log.debug(
                    "event=http_business_rejection status={} code={}",
                    exception.getStatus().value(),
                    exception.getMessageCode()
            );
        }
        return ResponseEntity
                .status(exception.getStatus())
                .body(ApiResponse.of(exception.getMessageCode(), exception.getData()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception
    ) {
        String messageCode = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .filter(this::isMessageCode)
                .findFirst()
                .orElse("invalid_request");

        log.debug("event=http_validation_rejection code={}", messageCode);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.success(messageCode));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException exception
    ) {
        log.debug("event=http_request_parameter_rejection parameter={}", exception.getName());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.success("invalid_request"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception exception) {
        log.error("event=unhandled_http_exception", exception);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.success("internal_server_error"));
    }

    private boolean isMessageCode(String message) {
        return message != null && MESSAGE_CODE_PATTERN.matcher(message).matches();
    }
}
