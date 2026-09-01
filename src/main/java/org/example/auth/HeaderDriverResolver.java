package org.example.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.example.exception.MissingDriverHeaderException;
import org.springframework.stereotype.Component;

/**
 * Stand-in temporal para auth real (ver javadoc de DriverResolver). Lee
 * X-Driver-Id / X-Driver-Name en vez de un Authorization: Bearer verificado.
 */
@Component
public class HeaderDriverResolver implements DriverResolver {

    public static final String DRIVER_ID_HEADER = "X-Driver-Id";
    public static final String DRIVER_NAME_HEADER = "X-Driver-Name";

    @Override
    public Driver resolve(HttpServletRequest request) {
        String id = request.getHeader(DRIVER_ID_HEADER);
        if (id == null || id.isBlank()) {
            throw new MissingDriverHeaderException(
                    "Falta el header " + DRIVER_ID_HEADER + " (stand-in temporal hasta que exista auth real).");
        }
        String name = request.getHeader(DRIVER_NAME_HEADER);
        return new Driver(id, name == null || name.isBlank() ? id : name);
    }
}
