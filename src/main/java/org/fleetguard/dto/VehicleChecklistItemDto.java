package org.fleetguard.dto;

/**
 * Ítem de la configuración del checklist de un vehículo (admin, CAM-31). origin: 'base' (catálogo
 * fijo) o 'extra' (agregado al vehículo). locked: obligatorio, no se puede quitar.
 */
public record VehicleChecklistItemDto(
        String id,
        String label,
        String type,
        String section,
        boolean required,
        String origin,
        boolean enabled,
        boolean locked
) {
}
