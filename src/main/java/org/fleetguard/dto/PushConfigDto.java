package org.fleetguard.dto;

/**
 * GET /api/push/public-key (CAM-119). publicKey es la clave VAPID pública que el navegador necesita
 * para suscribirse (applicationServerKey); null si el servidor no tiene las push configuradas.
 */
public record PushConfigDto(String publicKey) {
}
