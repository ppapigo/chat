package com.example.chat.auth;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ChatTokenAuthenticator {
    public static final String BEARER = "Bearer ";
    private final JwtTokenProvider tokenProvider;
    private final TokenDenylist tokenDenylist;
    private final BoardUserReader boardUserReader;

    public static Optional<String> extractToken(String authorizationHeader) {
        if (StringUtils.hasText(authorizationHeader) && authorizationHeader.startsWith(BEARER)){
            return Optional.of(authorizationHeader.substring(BEARER.length()));
    }
    return Optional.empty();
}


public Optional<ChatUserPrincipal> authenticate(String token){
        Optional<TokenClaims> parsed = tokenProvider.parse(token);
        if(parsed.isEmpty()){
            return Optional.empty();
        }

        TokenClaims claims = parsed.get();
      /*  if(tokenDenylist.isDenied(claims.jti())){
            return Optional.empty();
        }
*/
        return boardUserReader.findByUsername(claims.username())
                .map(chatUser -> new ChatUserPrincipal(
                        chatUser.id(),
                        chatUser.email(),
                        chatUser.nickName(),
                        claims.jti(),
                        claims.expiresAt()
                ));
        }
}
