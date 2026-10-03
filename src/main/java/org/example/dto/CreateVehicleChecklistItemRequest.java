package org.example.dto;

/**
 * Body de POST /api/vehicles/{id}/checklist-items (CAM-31). section: exterior|interior. type es
 * opcional (default check). enabled=false carga el ítem sin incluirlo en el checklist del chofer.
 */
public record CreateVehicleChecklistItemRequest(
        String label,
        String type,
        String section,
        Boolean enabled
) {
    public CreateVehicleChecklistItemRequest(String label, String type, String section) {
        this(label, type, section, null);
    }
}
