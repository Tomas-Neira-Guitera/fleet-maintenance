package org.fleetguard.storage;

import org.fleetguard.exception.UnsupportedPhotoTypeException;
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
import java.util.regex.Pattern;

/**
 * Implementación MVP de PhotoStorage: escribe archivos en un directorio local
 * (default ./uploads/photos, ver application.yml). Alcanza para dev/demos;
 * reemplazar por S3/GCS antes de correr en múltiples instancias.
 */
@Component
public class LocalFilesystemPhotoStorage implements PhotoStorage {

    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png"
    );

    // Letras, números, guion y guion bajo: sin puntos ni barras, no puede armar "../".
    private static final Pattern SAFE_PHOTO_ID = Pattern.compile("[A-Za-z0-9_-]+");

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
            throw UnsupportedPhotoTypeException.wrongType(contentType);
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
        // Defensa en profundidad contra path traversal: el id llega del path de
        // GET /api/photos/{id} y se usa para armar una ruta en disco. Tomcat ya rechaza "%2F" y
        // "%5C" en un segmento, pero acá no se confía en eso. Se aceptan ids "simples" (los UUID
        // que genera store y los "demo-*" del seed de datos demo) y, además, la ruta resuelta
        // tiene que quedar dentro del directorio de fotos.
        if (photoId == null || !SAFE_PHOTO_ID.matcher(photoId).matches()) {
            return Optional.empty();
        }
        Path root = storageDir.toAbsolutePath().normalize();
        for (Map.Entry<String, String> entry : EXTENSION_BY_CONTENT_TYPE.entrySet()) {
            Path candidate = root.resolve(photoId + "." + entry.getValue()).normalize();
            if (candidate.startsWith(root) && Files.exists(candidate)) {
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
