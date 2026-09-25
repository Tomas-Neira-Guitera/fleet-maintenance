package org.example.dto;

/** Body de POST /api/work-orders/{id}/photos -- photoUrl sale de POST /api/photos (paso 1). */
public record CreateWorkOrderPhotoRequest(
        String photoUrl
) {
}
