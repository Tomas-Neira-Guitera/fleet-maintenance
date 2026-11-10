package org.fleetguard.dto;

/** Una posición con cubierta montada, para que el chofer anote el dibujo en el pre-viaje (CAM-157). */
public record TirePositionDto(String position, String label) {
}
