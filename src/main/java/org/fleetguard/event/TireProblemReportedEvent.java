package org.fleetguard.event;

import java.util.List;
import java.util.UUID;

/** CAM-157: un chofer marcó neumáticos con problemas. tires: "Posición (neumático): estados", una por neumático. */
public record TireProblemReportedEvent(UUID vehicleId, String plate, String reportedBy, List<String> tires) {
}
