package com.example.chat.auth;

public interface TokenDenylist {
    boolean isDenied(String jti);
}
