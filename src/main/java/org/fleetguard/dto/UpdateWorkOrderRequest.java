package org.fleetguard.dto;

import java.math.BigDecimal;

/**
 * Body de PATCH /api/work-orders/{id} -- campos parciales. Avanzar/cancelar/finalizar
 * (status, + closingDescription/completedKm al finalizar) y editar datos (assignee,
 * executionType/externalProvider, description) pueden venir juntos o por separado.
 * completedKm es BigDecimal y no Long (CAM-74): con Long, Jackson trunca 1500.7 a 1500 en
 * silencio; así el decimal llega tal cual y WorkOrderService lo rechaza con 422.
 */
public record UpdateWorkOrderRequest(
        String status,
        String closingDescription,
        BigDecimal completedKm,
        String assignee,
        String technicianId,
        String executionType,
        String externalProvider,
        String description,
        // CAM-127: al finalizar una OT de un plan que renueva un documento, el documento nuevo (opcional).
        DocumentInputDto document,
        // CAM-148: al pasar a en_espera, el motivo (obligatorio) y un comentario (obligatorio si el motivo es "otro").
        String pauseReason,
        String pauseComment
) {

    /** Sin los campos de la pausa (CAM-148): para todo lo que no pasa a en_espera. */
    public UpdateWorkOrderRequest(String status, String closingDescription, BigDecimal completedKm, String assignee,
                                  String technicianId, String executionType, String externalProvider,
                                  String description, DocumentInputDto document) {
        this(status, closingDescription, completedKm, assignee, technicianId, executionType, externalProvider,
                description, document, null, null);
    }
}
