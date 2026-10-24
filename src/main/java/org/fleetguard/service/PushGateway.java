package org.fleetguard.service;

import org.fleetguard.entity.PushSubscription;

/**
 * Manda un mensaje al servicio de push de un navegador (CAM-119). Es una interfaz para que
 * PushNotificationService se pruebe sin salir a internet; la implementación es WebPushGateway.
 */
public interface PushGateway {

    /** false si el servidor no tiene claves VAPID: la push queda apagada y no se ofrece. */
    boolean isEnabled();

    /** Clave VAPID pública (base64url), o null si la push está apagada. */
    String publicKey();

    /**
     * Devuelve el código HTTP con que respondió el servicio de push: 201 si lo aceptó; 404 o 410 si
     * esa suscripción ya no existe (el usuario la revocó o el navegador la venció).
     *
     * @throws Exception si no se pudo ni mandar (red, cifrado)
     */
    int send(PushSubscription subscription, String payload) throws Exception;
}
