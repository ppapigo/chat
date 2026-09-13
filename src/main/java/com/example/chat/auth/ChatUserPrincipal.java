package com.example.chat.auth;


import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record ChatUserPrincipal(
        Long userId,
        String email,
        String nickName,
        String jti,
        Instant expiresAt
) implements Principal {
    @Override
    public String getName() {
        return email;
    }

    public boolean isExpired(Instant now){
        return !now.isBefore(expiresAt);
    }
    public Authentication toAuthentication(){
        return UsernamePasswordAuthenticationToken.authenticated(
                this,null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
    public static Optional<ChatUserPrincipal> from(Principal user){
        if(user instanceof Authentication authentication
        && authentication.getPrincipal() instanceof  ChatUserPrincipal principal){
            return  Optional.of(principal);
        }

        return  Optional.empty();
    }
}
