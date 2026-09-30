package org.example.dto;

/** Body de POST /api/vehicles/{id}/checklist-items (CAM-31). type: check|number, section: exterior|interior. */
public record CreateVehicleChecklistItemRequest(
        String label,
        String type,
        String section
) {
}
