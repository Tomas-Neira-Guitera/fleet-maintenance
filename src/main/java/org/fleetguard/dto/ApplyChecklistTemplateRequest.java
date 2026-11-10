package org.fleetguard.dto;

import java.util.List;

/** Body de POST /api/checklist-templates/{id}/apply (CAM-150). */
public record ApplyChecklistTemplateRequest(List<String> vehicleIds) {
}
