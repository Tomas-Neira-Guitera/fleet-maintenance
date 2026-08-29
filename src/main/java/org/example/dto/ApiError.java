package org.example.dto;

/** Matches components.schemas.ApiError in openapi.yaml. */
public record ApiError(String error, String message) {
}
