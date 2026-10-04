package com.example.chat.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

import java.nio.charset.StandardCharsets;


@Slf4j
@Component
public class StompErrorHandler extends StompSubProtocolErrorHandler {
    @Override
    public @Nullable Message<byte[]> handleClientMessageProcessingError(@Nullable Message<byte[]> clientMessage, Throwable ex) {
        ErrorCode errorCode = findErrorCode(ex);
        if (errorCode == ErrorCode.INTERNAL_SERVER_ERROR){
            log.error("STOMP 처리중 예상치 못 한 오류 발생함",ex);
        }else {
            log.warn("STOMP 에러 발생: code={}",errorCode.name());
        }
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setMessage(errorCode.getMessage());
        accessor.setNativeHeader("code", errorCode.name());
        accessor.setContentType(MimeTypeUtils.TEXT_PLAIN);
        accessor.setLeaveMutable(true);

        StompHeaderAccessor clientAccessor = clientMessage == null?null:
                MessageHeaderAccessor.getAccessor(clientMessage, StompHeaderAccessor.class);

        byte[] payload = errorCode.getMessage().getBytes(StandardCharsets.UTF_8);
        return handleInternal(accessor,payload,ex,clientAccessor);

    }

    private static ErrorCode findErrorCode(Throwable ex){
        for (Throwable t =ex; t!=null; t=t.getCause()){
            if (t instanceof StompAuthException authException){
                return authException.getErrorCode();
            }
        }
        return ErrorCode.INTERNAL_SERVER_ERROR;
    }
}
