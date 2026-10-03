package org.fleetguard.controller;

import org.fleetguard.dto.LoginResponseDto;
import org.fleetguard.dto.PhotoUploadResultDto;
import org.fleetguard.exception.InvalidCredentialsException;
import org.fleetguard.exception.UnsupportedPhotoTypeException;
import org.fleetguard.exception.UserInactiveException;
import org.fleetguard.service.AuthService;
import org.fleetguard.service.PhotoService;
import org.fleetguard.storage.PhotoStorage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Subida y descarga de fotos de defectos (CAM-11) y login (CAM-43/CAM-23). */
@WebMvcTest({PhotoController.class, AuthController.class})
class PhotoAndAuthControllerTest extends WebMvcTestBase {

    @MockBean
    private PhotoService photoService;

    @MockBean
    private AuthService authService;

    private static final MockMultipartFile JPG =
            new MockMultipartFile("file", "foto.jpg", "image/jpeg", new byte[]{1, 2, 3});

    @Test
    void subirUnaFotoDevuelve201ConSuUrl() throws Exception {
        when(photoService.upload(any())).thenReturn(new PhotoUploadResultDto("ph-1", "http://localhost:8080/api/photos/ph-1"));

        mvc.perform(multipart("/photos").file(JPG))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.photoUrl").value("http://localhost:8080/api/photos/ph-1"));
    }

    @Test
    void unaFotoQueNoEsJpgNiPngDevuelve415YUnaMuyPesadaDevuelve413() throws Exception {
        doThrow(UnsupportedPhotoTypeException.wrongType("image/heic")).when(photoService).upload(any());
        mvc.perform(multipart("/photos").file(JPG))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value("UNSUPPORTED_MEDIA_TYPE"));

        // En producción el 413 sale de Spring cuando el archivo pasa el límite de multipart
        // (spring.servlet.multipart.max-file-size = 8MB): es esa excepción la que se simula.
        doThrow(new MaxUploadSizeExceededException(8L * 1024 * 1024)).when(photoService).upload(any());
        mvc.perform(multipart("/photos").file(JPG))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error").value("PAYLOAD_TOO_LARGE"))
                .andExpect(jsonPath("$.message").value("La foto supera el tamaño máximo permitido (8MB)."));
    }

    @Test
    void subirSinElArchivoDevuelve400QueNombraLaParte() throws Exception {
        mvc.perform(multipart("/photos"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Falta la parte 'file' del formulario."));
    }

    @Test
    void descargarUnaFotoDevuelveLosBytesConSuTipo() throws Exception {
        when(photoService.get("ph-1")).thenReturn(Optional.of(new PhotoStorage.StoredPhotoContent(new byte[]{9, 8}, "image/png")));
        when(photoService.get("ph-x")).thenReturn(Optional.empty());

        mvc.perform(get("/photos/ph-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(content().bytes(new byte[]{9, 8}));
        mvc.perform(get("/photos/ph-x")).andExpect(status().isNotFound());
    }

    @Test
    void loginCorrectoDevuelveTokenYRol() throws Exception {
        when(authService.login(any())).thenReturn(new LoginResponseDto("jwt-firmado", "ADMIN"));

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-firmado"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void credencialesInvalidasDan401YUsuarioDesactivado403() throws Exception {
        doThrow(new InvalidCredentialsException()).when(authService).login(any());
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"a\",\"password\":\"b\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));

        doThrow(new UserInactiveException()).when(authService).login(any());
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"a\",\"password\":\"b\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("USER_INACTIVE"));
    }
}
