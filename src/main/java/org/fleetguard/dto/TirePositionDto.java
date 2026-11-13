package org.fleetguard.dto;

/** CAM-157: una posición del vehículo para el pre-viaje; el chofer solo puede marcar las que tienen cubierta. */
public record TirePositionDto(String position, String label, int axle, String side, String wheel, boolean mounted) {
}
