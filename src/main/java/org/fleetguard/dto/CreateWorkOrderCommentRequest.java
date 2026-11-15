package org.fleetguard.dto;

/** Body de POST /api/work-orders/{id}/comments (CAM-149). El autor sale del JWT, no de acá. */
public record CreateWorkOrderCommentRequest(String text) {
}
