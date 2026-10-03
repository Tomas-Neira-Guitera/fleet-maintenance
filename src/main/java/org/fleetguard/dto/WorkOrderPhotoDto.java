package org.fleetguard.dto;

public record WorkOrderPhotoDto(
        String id,
        String photoUrl,
        String createdAt
) {
}
