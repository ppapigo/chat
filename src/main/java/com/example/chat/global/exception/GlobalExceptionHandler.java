package com.example.chat.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.nio.file.AccessDeniedException;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();

        log.warn(
                "BusinessException: code={}, message={}",
                errorCode.name(),
                errorCode.getMessage()
        );

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException e
    ) {

        List<ErrorResponse.FieldErrorDetail> errors =
                e.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .map(fieldError ->
                                new ErrorResponse.FieldErrorDetail(
                                        fieldError.getField(),
                                        fieldError.getDefaultMessage()
                                )
                        )
                        .toList();

        log.warn("Validation Failed: {}", errors);

        return ResponseEntity
                .status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ErrorResponse.of(
                        ErrorCode.INVALID_INPUT,
                        errors
                ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException e
    ) {

        log.warn("Access Denied: {}", e.getMessage());

        return ResponseEntity
                .status(ErrorCode.ACCESS_DENIED.getStatus())
                .body(ErrorResponse.of(ErrorCode.ACCESS_DENIED));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(
            NoResourceFoundException e
    ) {

        log.warn("Resource Not Found: {}", e.getMessage());

        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR));
    }
}