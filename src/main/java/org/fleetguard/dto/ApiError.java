package org.fleetguard.dto;

/** Coincide con components.schemas.ApiError de openapi.yaml. */
public record ApiError(String error, String message) {
}
