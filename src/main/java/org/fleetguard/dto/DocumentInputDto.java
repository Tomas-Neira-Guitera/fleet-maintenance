package org.fleetguard.dto;

/**
 * El documento renovado que se carga junto con un mantenimiento hecho (CAM-127): al registrar el
 * completion de un plan vinculado, o al finalizar su OT. expiresAt obligatorio; se emite en la fecha del completion.
 */
public record DocumentInputDto(String number, String expiresAt, String fileUrl) {
}
