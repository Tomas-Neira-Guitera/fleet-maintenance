package org.fleetguard.event;

import org.fleetguard.entity.InspectionType;

import java.util.List;
import java.util.UUID;

/** CAM-97: una inspección se guardó con al menos un defecto bloqueante. Trae todo lo que dice el aviso. */
public record BlockingDefectReportedEvent(UUID vehicleId, String plate, InspectionType inspectionType,
                                          String driverName, List<String> defectTitles) {
}
