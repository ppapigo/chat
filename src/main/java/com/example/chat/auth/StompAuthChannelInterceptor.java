package com.example.chat.auth;

import com.example.chat.global.exception.ErrorCode;
import com.example.chat.global.exception.StompAuthException;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final ChatTokenAuthenticator authenticator;
    private final JwtTokenProvider tokenProvider;
    private final TokenDenylist tokenDenylist;

    @Override
    public @Nullable Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel){
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand()==null){
            return message;
        }

        switch (accessor.getCommand()){
            case CONNECT -> authenticateConnect(accessor);
            case SEND,SUBSCRIBE -> requireLivePrincipal(accessor);
            default -> {}
        }

        return message;
    }

    private void authenticateConnect(StompHeaderAccessor accessor){
        String token = ChatTokenAuthenticator.extractToken(
                accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION))
                .orElseThrow(()->new StompAuthException(ErrorCode.LOGIN_REQUIRED));

        ChatUserPrincipal principal = authenticator.authenticate(token)
            .orElseThrow(()->
                    new StompAuthException(tokenProvider.isExpired(token) ? ErrorCode.TOKEN_EXPIRED: ErrorCode.LOGIN_REQUIRED)
                    );


        accessor.setUser(principal.toAuthentication());
    }

    private void requireLivePrincipal(StompHeaderAccessor accessor){
        ChatUserPrincipal principal = ChatUserPrincipal.from(accessor.getUser())
                .orElseThrow(()->new StompAuthException(ErrorCode.LOGIN_REQUIRED));

        if (principal.isExpired(Instant.now())){
            throw new StompAuthException(ErrorCode.TOKEN_EXPIRED);
        }

        if (tokenDenylist.isDenied(principal.jti())){
            throw new StompAuthException(ErrorCode.LOGIN_REQUIRED);
        }
    }
}
