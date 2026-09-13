package com.example.chat.global.exception;

public class UnAuthorizedException extends BusinessException {
    public UnAuthorizedException(ErrorCode errorCode ) {
        super(errorCode);
    }
}
