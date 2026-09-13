package com.example.chat.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Optional;

@Slf4j
@Component
public class JwtTokenProvider {
    private final SecretKey key;
    public JwtTokenProvider(@Value("${jwt.secret}")String base64Secret){
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));

    }

    public Optional<TokenClaims> parse(String token){
        try{
            Claims claims = Jwts.parser().verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if( claims.getId() == null || claims.getSubject()==null || claims.getExpiration()==null){
                return  Optional.empty();
            }

            return Optional.of(new TokenClaims(
                    claims.getSubject(),
                    claims.getId(),
                    claims.getExpiration().toInstant()
            ));
        }catch (JwtException | IllegalArgumentException e){
            log.debug("Invalid jwt: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public boolean isExpired(String token) {
        try {
            // token이 파싱이 완료되지 않음
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);

            return false;

        } catch (ExpiredJwtException e) {
            return true;
      /*  } catch (JwtException | IllegalArgumentException e) {
            return true;
        } */
        }

    }
}

