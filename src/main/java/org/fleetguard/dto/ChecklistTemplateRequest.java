package org.fleetguard.dto;

import java.util.List;

/** Body de POST y PUT /api/checklist-templates (CAM-150). El PUT reemplaza el nombre y todos los controles. */
public record ChecklistTemplateRequest(String name, List<ChecklistTemplateItemInput> items) {
}
