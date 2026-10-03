package org.fleetguard.controller;

import org.fleetguard.service.VehicleHistoryService;
import org.fleetguard.service.VehicleService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Errores que no son de dominio, iguales para cualquier endpoint (se prueban sobre /vehicles):
 * un pedido mal armado tiene que responder 4xx con el formato {error, message} de siempre, y un
 * error inesperado, 500 sin filtrar detalles internos. Antes de CAM-78, todos estos casos
 * caían en el handler genérico y salían como 500.
 */
@WebMvcTest(VehicleController.class)
class ApiErrorHandlingTest extends WebMvcTestBase {

    @MockBean
    private VehicleService vehicleService;

    @MockBean
    private VehicleHistoryService vehicleHistoryService;

    @Test
    void unJsonMalFormadoDevuelve400() throws Exception {
        mvc.perform(post("/vehicles").contentType(MediaType.APPLICATION_JSON).content("{\"plate\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"));
    }

    @Test
    void unParametroConTipoInvalidoDevuelve400() throws Exception {
        mvc.perform(get("/vehicles").param("active", "quizas"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El parámetro 'active' tiene un valor inválido."));
    }

    @Test
    void unMetodoNoSoportadoDevuelve405() throws Exception {
        mvc.perform(put("/vehicles"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unContentTypeNoSoportadoDevuelve415() throws Exception {
        mvc.perform(post("/vehicles").contentType(MediaType.TEXT_PLAIN).content("AB123CD"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void unaRutaQueNoExisteDevuelve404() throws Exception {
        mvc.perform(get("/vehiculos"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void unErrorInesperadoDevuelve500SinFiltrarElDetalleInterno() throws Exception {
        when(vehicleService.listVehicles(true)).thenThrow(new IllegalStateException("password=secreto en la conexión"));

        mvc.perform(get("/vehicles"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Ocurrió un error inesperado."))
                .andExpect(content().string(not(containsString("secreto"))));
    }
}
