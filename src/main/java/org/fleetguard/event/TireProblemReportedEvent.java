package org.fleetguard.event;

import java.util.List;
import java.util.UUID;

/** CAM-157: un chofer marcó cubiertas con problemas. tires: "Posición (cubierta): estados", una por cubierta. */
public record TireProblemReportedEvent(UUID vehicleId, String plate, String reportedBy, List<String> tires) {
}
