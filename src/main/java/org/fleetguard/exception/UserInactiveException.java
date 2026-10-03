package org.fleetguard.exception;

/** 403 del login: usuario y contraseña correctos, pero el usuario está desactivado (CAM-23). */
public class UserInactiveException extends RuntimeException {
    public UserInactiveException() {
        super("Tu usuario está desactivado. Pedile a un administrador que lo reactive.");
    }
}
