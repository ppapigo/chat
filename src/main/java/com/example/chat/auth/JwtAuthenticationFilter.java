package com.example.chat.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final ChatTokenAuthenticator authenticator;
    private final HandlerExceptionResolver handlerExceptionResolver;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            Optional<String> token =
                    ChatTokenAuthenticator.extractToken(
                            request.getHeader(HttpHeaders.AUTHORIZATION)
                    );

            if (token.isPresent()) {
                authenticator.authenticate(token.get())
                        .ifPresent(principal -> {

                            UsernamePasswordAuthenticationToken authentication =
                                    (UsernamePasswordAuthenticationToken) principal.toAuthentication();

                            authentication.setDetails(
                                    new WebAuthenticationDetailsSource()
                                            .buildDetails(request)
                            );

                            SecurityContextHolder.getContext()
                                    .setAuthentication(authentication);
                        });
            }

            filterChain.doFilter(request, response);

        } catch (Exception e) {
            SecurityContextHolder.clearContext();

            log.warn("JWT authentication failed: {}", e.getMessage());

            handlerExceptionResolver.resolveException(
                    request,
                    response,
                    null,
                    e
            );
        }
    }
}
   /* private Optional<String> resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith(BEARER)) {
            return Optional.of(header.substring(BEARER.length()));
        }

        return Optional.empty();
    }*/

