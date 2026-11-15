package org.fleetguard.dto;

/** Un control al crear o editar una plantilla (CAM-150). type opcional (default check); section: exterior|interior. */
public record ChecklistTemplateItemInput(String label, String type, String section) {
}
