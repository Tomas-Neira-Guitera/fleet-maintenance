package org.example.auth;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resuelve la identidad del chofer. Seam temporal: {@link HeaderDriverResolver}
 * es un stand-in hasta que exista login/roles real; el resto de la app
 * depende de esta interfaz, no del header.
 */
public interface DriverResolver {

    Driver resolve(HttpServletRequest request);
}
