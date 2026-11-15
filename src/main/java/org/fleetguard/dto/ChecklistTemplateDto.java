package org.fleetguard.dto;

import java.util.List;

/** Plantilla de checklist (CAM-150). */
public record ChecklistTemplateDto(String id, String name, List<Item> items, String createdAt) {

    public record Item(String id, String label, String type, String section) {
    }
}
