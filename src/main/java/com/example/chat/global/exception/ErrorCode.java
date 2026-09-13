package com.example.chat.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    LOGIN_REQUIRED(HttpStatus.UNAUTHORIZED,"로그인이 필요합니다"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Access Token이 만료되었습니다. 재발급 후 다시 연결하세요"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN,"접근권한이 없습니다"),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다"),

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "요청 본문을 읽을 수 없습니다"),

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR,"서버 내부 오류가 발생했습니다");

    private final HttpStatus status;
    private final String message;

}
