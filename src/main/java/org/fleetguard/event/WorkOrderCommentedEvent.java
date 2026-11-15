package org.fleetguard.event;

import org.fleetguard.entity.Role;

import java.util.UUID;

/** CAM-149: alguien comentó en una orden de trabajo. El aviso sale después del commit. */
public record WorkOrderCommentedEvent(UUID workOrderId, UUID authorId, String authorName, Role authorRole, String text) {
}
