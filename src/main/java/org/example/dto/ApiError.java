package org.example.dto;

/** Coincide con components.schemas.ApiError de openapi.yaml. */
public record ApiError(String error, String message) {
}
