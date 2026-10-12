package org.fleetguard.repository;

/**
 * Fila de una consulta agrupada por chofer de la inspección (CAM-91). driverId es texto: las
 * inspecciones anteriores a CAM-85 no guardaban el id de un usuario.
 */
public record DriverCount(String driverId, long count) {
}
