package org.example.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * Guarda una foto de defecto subida y devuelve de dónde leerla después.
 * Seam para reemplazar la implementación local por una en la nube sin
 * tocar PhotoController.
 */
public interface PhotoStorage {

    record StoredPhoto(String photoId, String relativeUrl) {
    }

    StoredPhoto store(MultipartFile file);

    /** Bytes + content type para GET /api/photos/{id}, o vacío si no existe. */
    java.util.Optional<StoredPhotoContent> read(String photoId);

    record StoredPhotoContent(byte[] bytes, String contentType) {
    }
}
