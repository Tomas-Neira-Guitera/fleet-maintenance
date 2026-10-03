package org.fleetguard.dto;

/** Body de PATCH /api/vehicles/{id}/checklist-items/{itemId} (CAM-31): activar o quitar el ítem. */
public record UpdateVehicleChecklistItemRequest(
        Boolean enabled
) {
}
