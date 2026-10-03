package org.fleetguard.storage;

import org.fleetguard.exception.UnsupportedPhotoTypeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guardado de fotos de defectos y de OTs en disco, contra un directorio temporal real. Lo que
 * importa: que lo que se guarda se pueda volver a leer con su tipo, y que el id que llega por
 * la URL no permita leer archivos fuera del directorio de fotos.
 */
class LocalFilesystemPhotoStorageTest {

    @TempDir
    Path tempDir;

    private LocalFilesystemPhotoStorage storage(Path dir) {
        return new LocalFilesystemPhotoStorage(dir.toString());
    }

    @Test
    void unaFotoGuardadaSeLeeConSusBytesYSuTipo() {
        LocalFilesystemPhotoStorage storage = storage(tempDir);
        byte[] png = {(byte) 0x89, 'P', 'N', 'G'};

        PhotoStorage.StoredPhoto stored = storage.store(new MockMultipartFile("file", "f.png", "image/png", png));

        assertEquals("/photos/" + stored.photoId(), stored.relativeUrl());
        Optional<PhotoStorage.StoredPhotoContent> read = storage.read(stored.photoId());
        assertTrue(read.isPresent());
        assertArrayEquals(png, read.get().bytes());
        assertEquals("image/png", read.get().contentType());
        assertTrue(Files.exists(tempDir.resolve(stored.photoId() + ".png")));
    }

    @Test
    void cadaFotoRecibeUnIdNuevoAunqueSeLlameIgual() {
        LocalFilesystemPhotoStorage storage = storage(tempDir);
        MockMultipartFile file = new MockMultipartFile("file", "foto.jpg", "image/jpeg", new byte[]{1});

        // El nombre original no se usa: dos fotos "foto.jpg" no se pisan.
        assertTrue(!storage.store(file).photoId().equals(storage.store(file).photoId()));
    }

    @Test
    void unTipoQueNoEsJpgNiPngNoSeGuarda() throws Exception {
        LocalFilesystemPhotoStorage storage = storage(tempDir);

        assertThrows(UnsupportedPhotoTypeException.class,
                () -> storage.store(new MockMultipartFile("file", "f.webp", "image/webp", new byte[]{1})));
        try (var files = Files.list(tempDir)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void lasFotosDelSeedDemoConIdLegibleSeLeen() throws Exception {
        // docs/db/seed-demo.sql usa ids como "demo-faro-roto" (no UUID) y copia los .jpg a mano.
        Files.write(tempDir.resolve("demo-faro-roto.jpg"), new byte[]{7});

        Optional<PhotoStorage.StoredPhotoContent> read = storage(tempDir).read("demo-faro-roto");

        assertTrue(read.isPresent());
        assertEquals("image/jpeg", read.get().contentType());
    }

    @Test
    void unaFotoQueNoExisteDevuelveVacio() {
        assertTrue(storage(tempDir).read("6f1c2a54-3b7d-4c39-9a51-2f1f0c6b7e10").isEmpty());
    }

    @Test
    void unIdQueIntentaSalirDelDirectorioNoLeeNada() throws Exception {
        // Un .png legítimo fuera del directorio de fotos, al lado.
        Path photosDir = Files.createDirectory(tempDir.resolve("photos"));
        Files.write(tempDir.resolve("secreto.png"), new byte[]{42});
        LocalFilesystemPhotoStorage storage = storage(photosDir);

        assertTrue(storage.read("../secreto").isEmpty());
        assertTrue(storage.read("..\\secreto").isEmpty());
        assertTrue(storage.read(null).isEmpty());
    }

    @Test
    void creaElDirectorioSiNoExiste() {
        Path nested = tempDir.resolve("uploads").resolve("photos");

        storage(nested);

        assertTrue(Files.isDirectory(nested));
    }
}
