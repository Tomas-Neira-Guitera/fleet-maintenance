package org.example.dto;

/**
 * Body de PATCH /api/work-orders/{id} -- campos parciales. Avanzar/cancelar/finalizar
 * (status, + closingDescription/completedKm al finalizar) y editar datos (assignee,
 * executionType/externalProvider, description) pueden venir juntos o por separado.
 */
public record UpdateWorkOrderRequest(
        String status,
        String closingDescription,
        Long completedKm,
        String assignee,
        String executionType,
        String externalProvider,
        String description
) {
}
