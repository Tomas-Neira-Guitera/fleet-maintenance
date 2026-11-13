package org.fleetguard.dto;

import java.util.List;

/**
 * GET /api/vehicles/{id}/coupling: con quién está enganchado el vehículo ahora. En un remolque, towedBy (y towing
 * null); en un tractor o camión rígido, towing (y towedBy null). canTow: los tipos que puede arrastrar.
 */
public record CouplingDto(String vehicleId, VehicleRef towedBy, VehicleRef towing, List<String> canTow,
                          List<HistoryItem> history) {

    public record VehicleRef(String id, String plate, String vehicleType, String coupledAt) {
    }

    public record HistoryItem(String trailerId, String trailerPlate, String towingId, String towingPlate,
                              String coupledAt, String uncoupledAt, String coupledBy) {
    }
}
