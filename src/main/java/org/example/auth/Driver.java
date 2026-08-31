package org.example.auth;

/** Chofer de la request, resuelto server-side -- nunca se confía en el body. */
public record Driver(String id, String name) {
}
