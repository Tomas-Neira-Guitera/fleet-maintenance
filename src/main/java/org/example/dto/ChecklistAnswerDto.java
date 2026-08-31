package org.example.dto;

/**
 * Coincide con components.schemas.ChecklistAnswer de openapi.yaml. El servidor
 * resuelve label/sección/tipo/obligatoriedad desde su propio catálogo, nunca
 * desde metadata enviada por el cliente (ver CAM-11-dvir-contract.md, decisión #2).
 */
public record ChecklistAnswerDto(String itemId, String outcome, Double numberValue, DefectDetailDto defect) {
}
