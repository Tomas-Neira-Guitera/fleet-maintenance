package org.fleetguard.service;

import org.fleetguard.dto.PhotoUploadResultDto;
import org.fleetguard.exception.UnsupportedPhotoTypeException;
import org.fleetguard.storage.PhotoStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Subida de fotos (CAM-11): solo JPG o PNG, y la URL devuelta es absoluta, porque el frontend
 * la guarda tal cual en el defecto y la muestra desde otro origen (ver STATE.md, fotos de
 * prueba con URL relativa que no se podían ver).
 */
class PhotoServiceTest {

    private final PhotoStorage storage = mock(PhotoStorage.class);
    private final PhotoService service = new PhotoService(storage);

    @BeforeEach
    void setUp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);
        request.setContextPath("/api");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void unaFotoValidaDevuelveSuUrlAbsoluta() {
        when(storage.store(any())).thenReturn(new PhotoStorage.StoredPhoto("ph-1", "/photos/ph-1"));

        PhotoUploadResultDto result = service.upload(new MockMultipartFile("file", "f.jpg", "image/jpeg", new byte[]{1}));

        assertEquals("ph-1", result.photoId());
        assertEquals("http://localhost:8080/api/photos/ph-1", result.photoUrl());
    }

    @Test
    void unArchivoVacioOConOtroFormatoSeRechazaSinGuardarlo() {
        assertThrows(UnsupportedPhotoTypeException.class,
                () -> service.upload(new MockMultipartFile("file", "f.jpg", "image/jpeg", new byte[0])));
        UnsupportedPhotoTypeException heic = assertThrows(UnsupportedPhotoTypeException.class,
                () -> service.upload(new MockMultipartFile("file", "f.heic", "image/heic", new byte[]{1})));

        // El mensaje nombra el formato como lo conoce el usuario (HEIC), no el MIME type.
        assertTrue(heic.getMessage().contains("HEIC"));
        verify(storage, never()).store(any());
    }

    @Test
    void sinTipoDeContenidoElMensajeIgualEsClaro() {
        UnsupportedPhotoTypeException ex = assertThrows(UnsupportedPhotoTypeException.class,
                () -> service.upload(new MockMultipartFile("file", "f", null, new byte[]{1})));

        assertTrue(ex.getMessage().contains("de un formato que no reconocemos"));
    }

    @Test
    void elComprobanteDeUnDocumentoPuedeSerPdfPeroUnaFotoNo() {
        when(storage.store(any())).thenReturn(new PhotoStorage.StoredPhoto("doc-1", "/photos/doc-1"));
        MockMultipartFile pdf = new MockMultipartFile("file", "poliza.pdf", "application/pdf", new byte[]{1});

        // CAM-127: ?purpose=document acepta PDF (pólizas, cédulas).
        assertEquals("doc-1", service.upload(pdf, PhotoService.PURPOSE_DOCUMENT).photoId());
        UnsupportedPhotoTypeException photo = assertThrows(UnsupportedPhotoTypeException.class, () -> service.upload(pdf));
        assertTrue(photo.getMessage().startsWith("La foto tiene que ser JPG o PNG"));
        UnsupportedPhotoTypeException doc = assertThrows(UnsupportedPhotoTypeException.class, () -> service.upload(
                new MockMultipartFile("file", "f.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        new byte[]{1}), PhotoService.PURPOSE_DOCUMENT));
        assertTrue(doc.getMessage().startsWith("El archivo tiene que ser JPG, PNG o PDF"));
    }
}
