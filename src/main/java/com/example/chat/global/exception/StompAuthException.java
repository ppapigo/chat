package com.example.chat.global.exception;

import lombok.Getter;
import lombok.Setter;
import org.springframework.messaging.MessagingException;

@Getter
public class StompAuthException extends MessagingException {
    private final ErrorCode errorCode;

    public StompAuthException(ErrorCode errorCode){
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
