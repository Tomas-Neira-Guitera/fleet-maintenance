package org.fleetguard.dto;

/** CAM-148: por qué está en espera una orden de trabajo y desde cuándo. comment puede ser null. */
public record WorkOrderPauseDto(String reason, String comment, String since) {
}
