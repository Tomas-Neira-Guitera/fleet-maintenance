package org.example.storage;

import org.example.exception.UnsupportedPhotoTypeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * MVP implementation of PhotoStorage: writes files to a local directory on
 * disk (default ./uploads/photos, see application.yml
 * fleetguard.photos.storage-dir) and serves them back through
 * GET /api/photos/{id} (see PhotoController). Good enough for local dev and
 * demos; swap for an S3/GCS-backed implementation before this needs to run
 * across multiple instances or survive a redeploy.
 */
@Component
public class LocalFilesystemPhotoStorage implements PhotoStorage {

    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png"
    );

    private final Path storageDir;

    public LocalFilesystemPhotoStorage(@Value("${fleetguard.photos.storage-dir}") String storageDir) {
        this.storageDir = Path.of(storageDir);
        try {
            Files.createDirectories(this.storageDir);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo crear el directorio de fotos: " + this.storageDir, e);
        }
    }

    @Override
    public StoredPhoto store(MultipartFile file) {
        String contentType = file.getContentType();
        String extension = EXTENSION_BY_CONTENT_TYPE.get(contentType);
        if (extension == null) {
            throw new UnsupportedPhotoTypeException(
                    "El archivo debe ser image/jpeg o image/png (recibido: " + contentType + ").");
        }

        String photoId = UUID.randomUUID().toString();
        Path target = storageDir.resolve(photoId + "." + extension);
        try {
            file.transferTo(target);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la foto", e);
        }
        return new StoredPhoto(photoId, "/photos/" + photoId);
    }

    @Override
    public Optional<StoredPhotoContent> read(String photoId) {
        for (Map.Entry<String, String> entry : EXTENSION_BY_CONTENT_TYPE.entrySet()) {
            Path candidate = storageDir.resolve(photoId + "." + entry.getValue());
            if (Files.exists(candidate)) {
                try {
                    return Optional.of(new StoredPhotoContent(Files.readAllBytes(candidate), entry.getKey()));
                } catch (IOException e) {
                    throw new UncheckedIOException("No se pudo leer la foto " + photoId, e);
                }
            }
        }
        return Optional.empty();
    }
}
