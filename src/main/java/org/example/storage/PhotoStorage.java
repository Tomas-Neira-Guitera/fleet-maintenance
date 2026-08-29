package org.example.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * Stores an uploaded defect photo and returns where it can be read back from.
 * <p>
 * There's no cloud storage configured in this repo yet and the OpenAPI
 * contract doesn't dictate where files physically live -- only that
 * POST /api/photos returns a photoUrl that later resolves. This interface is
 * the seam to swap the local-filesystem implementation for a real
 * cloud-backed one later without touching PhotoController.
 */
public interface PhotoStorage {

    record StoredPhoto(String photoId, String relativeUrl) {
    }

    StoredPhoto store(MultipartFile file);

    /** Raw bytes + content type for GET /api/photos/{id}, or empty if unknown. */
    java.util.Optional<StoredPhotoContent> read(String photoId);

    record StoredPhotoContent(byte[] bytes, String contentType) {
    }
}
