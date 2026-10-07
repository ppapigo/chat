package com.example.chat.global.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private String code;
    private String message;
    private LocalDateTime timeStamp;
    List<FieldErrorDetail> errors;

    public record FieldErrorDetail(String field, String reason){}

    public static ErrorResponse of(ErrorCode errorCode){
        return new ErrorResponse(
                errorCode.name(),
                errorCode.getMessage(),
                LocalDateTime.now(),
                null);
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldErrorDetail> errors){
        return new ErrorResponse(
                errorCode.name(),
                errorCode.getMessage(),
                LocalDateTime.now(),
                errors);

    }
}
