package org.example.service;

import org.example.dto.PhotoUploadResultDto;
import org.example.exception.UnsupportedPhotoTypeException;
import org.example.storage.PhotoStorage;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Optional;
import java.util.Set;

/** Lógica de negocio de POST /api/photos + GET /api/photos/{id} -- ver openapi.yaml. */
@Service
public class PhotoService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");

    private final PhotoStorage photoStorage;

    public PhotoService(PhotoStorage photoStorage) {
        this.photoStorage = photoStorage;
    }

    public PhotoUploadResultDto upload(MultipartFile file) {
        if (file.isEmpty()) {
            throw new UnsupportedPhotoTypeException("El archivo está vacío.");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new UnsupportedPhotoTypeException(
                    "El archivo debe ser image/jpeg o image/png (recibido: " + file.getContentType() + ").");
        }

        PhotoStorage.StoredPhoto stored = photoStorage.store(file);
        String absoluteUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(stored.relativeUrl())
                .toUriString();
        return new PhotoUploadResultDto(stored.photoId(), absoluteUrl);
    }

    public Optional<PhotoStorage.StoredPhotoContent> get(String photoId) {
        return photoStorage.read(photoId);
    }
}
