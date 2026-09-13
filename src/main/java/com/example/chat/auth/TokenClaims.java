package com.example.chat.auth;

import java.time.Instant;


public record TokenClaims(String username, String jti, Instant expiresAt) {
}
