package org.example.auth;

/** The driver making the request, resolved server-side -- never trusted from the request body. */
public record Driver(String id, String name) {
}
