package com.example.chat.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisTokenDenylist implements TokenDenylist{
    private static final String KEY_PREFIX="deny:";

    private final StringRedisTemplate redis;

    @Override
    public boolean isDenied(String jti) {
        return redis.hasKey(KEY_PREFIX + jti);
    }
}
