package org.example.exception;

/** 401: falta el token, es inválido o vencido, o el usuario ya no existe o está desactivado (CAM-23). */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException() {
        super("Tu sesión no es válida o venció. Volvé a iniciar sesión.");
    }
}
