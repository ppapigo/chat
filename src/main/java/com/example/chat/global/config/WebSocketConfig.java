package com.example.chat.global.config;

import com.example.chat.auth.StompAuthChannelInterceptor;
import com.example.chat.global.exception.StompErrorHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final StompAuthChannelInterceptor authInterceptor;
    private final StompErrorHandler errorHandler;
    private final String[] allowedOriginPatterns;

    public WebSocketConfig(
            StompAuthChannelInterceptor authInterceptor,
            StompErrorHandler errorHandler,
            @Value("${app.ws.allowed-origin-patterns}") String[] allowedOriginPatterns
    ) {
        this.authInterceptor = authInterceptor;
        this.errorHandler = errorHandler;
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.setErrorHandler(errorHandler);
        registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOriginPatterns);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}

