package org.fleetguard.dto;

import java.util.List;

/** Body de POST /api/vehicles/import (CAM-154): las filas del archivo, ya leídas por el navegador. */
public record ImportVehiclesRequest(List<CreateVehicleRequest> rows) {
}
