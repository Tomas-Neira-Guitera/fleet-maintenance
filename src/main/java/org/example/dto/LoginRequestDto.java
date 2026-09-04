package org.example.dto;

/** Body de POST /api/auth/login. */
public record LoginRequestDto(String username, String password) {
}
