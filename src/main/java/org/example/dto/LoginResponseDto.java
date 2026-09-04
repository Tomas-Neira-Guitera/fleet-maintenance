package org.example.dto;

/** Respuesta 200 de POST /api/auth/login. */
public record LoginResponseDto(String token, String role) {
}
