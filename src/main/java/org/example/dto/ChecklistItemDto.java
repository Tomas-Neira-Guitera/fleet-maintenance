package org.example.dto;

/** Ítem del checklist que ve el chofer -- GET /api/vehicles/{id}/checklist (CAM-31). */
public record ChecklistItemDto(
        String id,
        String label,
        String type,
        String section,
        boolean required
) {
}
