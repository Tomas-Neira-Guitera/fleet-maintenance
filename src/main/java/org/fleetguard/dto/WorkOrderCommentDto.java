package org.fleetguard.dto;

/**
 * CAM-149: coincide con components.schemas.WorkOrderComment de openapi.yaml. author.name es el nombre actual
 * del usuario (o el que tenía al comentar, si ya no existe); authorRole, el rol con que comentó (ADMIN o TECNICO).
 */
public record WorkOrderCommentDto(String id, UserRefDto author, String authorRole, String text, String createdAt) {
}
