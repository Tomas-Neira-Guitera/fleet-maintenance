package org.example.entity.checklist;

/**
 * Definición server-side de un ítem del checklist: id, label, tipo, sección
 * y si es obligatorio (ver CAM-11-dvir-contract.md, decisión #2).
 */
public record ChecklistItemDef(
        String id,
        String label,
        ChecklistItemType type,
        ChecklistSection section,
        boolean required
) {
    public ChecklistItemDef(String id, String label, ChecklistItemType type, ChecklistSection section) {
        this(id, label, type, section, false);
    }
}
