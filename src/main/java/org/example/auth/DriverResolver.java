package org.example.auth;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the calling driver's identity from the request.
 * <p>
 * TEMPORARY SEAM: per CAM-11-dvir-contract.md, real login/roles are "historia
 * aparte" -- out of scope here. {@link HeaderDriverResolver} is a stand-in
 * that trusts a request header instead of a verified auth token, purely so
 * the rest of the app has one place to swap out once real auth lands. Every
 * other class in the app depends on this interface, never on the header
 * directly, so that swap should be a one-file change.
 */
public interface DriverResolver {

    Driver resolve(HttpServletRequest request);
}
