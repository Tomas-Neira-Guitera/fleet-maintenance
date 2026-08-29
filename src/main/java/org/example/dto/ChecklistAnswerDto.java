package org.example.dto;

/**
 * Matches components.schemas.ChecklistAnswer in openapi.yaml. On the way in,
 * only itemId/outcome/numberValue/defect are read -- the server resolves
 * everything else (label/section/type/required) from its own catalog, never
 * from client-sent metadata (see CAM-11-dvir-contract.md decision #2).
 */
public record ChecklistAnswerDto(String itemId, String outcome, Double numberValue, DefectDetailDto defect) {
}
