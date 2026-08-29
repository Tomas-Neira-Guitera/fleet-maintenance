package org.example.checklist;

/**
 * Server-side definition of one checklist item: id, label, type, section and
 * whether it's required. This is the catalog the server resolves every
 * incoming {@code itemId} against -- the client only ever sends itemId plus
 * whatever the driver filled in (outcome/numberValue/defect), never this
 * metadata (see CAM-11-dvir-contract.md, decision #2).
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
