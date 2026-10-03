package org.fleetguard.controller;

import org.fleetguard.auth.Driver;
import org.fleetguard.auth.HeaderDriverResolver;
import org.fleetguard.dto.DefectDto;
import org.fleetguard.dto.InspectionDto;
import org.fleetguard.dto.InspectionResultDto;
import org.fleetguard.dto.TripDto;
import org.fleetguard.dto.ValidationErrorDetail;
import org.fleetguard.exception.InspectionValidationException;
import org.fleetguard.exception.VehicleStateConflictException;
import org.fleetguard.service.DefectService;
import org.fleetguard.service.InspectionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Envío de inspecciones (CAM-11) y listado de defectos (CAM-13). El chofer se resuelve con el
 * HeaderDriverResolver real: hasta que las inspecciones usen el JWT (CAM-73), el header
 * X-Driver-Id es lo único que identifica quién hizo la inspección.
 */
@WebMvcTest({InspectionController.class, DefectController.class})
@Import(HeaderDriverResolver.class)
class InspectionAndDefectControllerTest extends WebMvcTestBase {

    @MockBean
    private InspectionService inspectionService;

    @MockBean
    private DefectService defectService;

    private static final String PRE_TRIP = "{\"type\":\"pre-trip\",\"answers\":[]}";

    private static InspectionResultDto result() {
        InspectionDto inspection = new InspectionDto("i-1", "t-1", "v-1", "driver-1", "pre-trip", "2026-10-03T12:00:00Z",
                12345.0, List.of(), null, false);
        return new InspectionResultDto(inspection, new TripDto("t-1", "v-1", "open", "2026-10-03T12:00:00Z", null));
    }

    @Test
    void enviarUnaInspeccionDevuelve201ConElViajeAbierto() throws Exception {
        when(inspectionService.submit(eq("v-1"), any(), eq(new Driver("driver-1", "Carlos Gómez")))).thenReturn(result());

        mvc.perform(post("/inspections/v-1").contentType(MediaType.APPLICATION_JSON).content(PRE_TRIP)
                        .header("X-Driver-Id", "driver-1").header("X-Driver-Name", "Carlos Gómez"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.inspection.type").value("pre-trip"))
                .andExpect(jsonPath("$.trip.status").value("open"));
    }

    @Test
    void sinNombreDeChoferSeUsaSuIdComoNombre() throws Exception {
        when(inspectionService.submit(eq("v-1"), any(), any())).thenReturn(result());

        mvc.perform(post("/inspections/v-1").contentType(MediaType.APPLICATION_JSON).content(PRE_TRIP)
                        .header("X-Driver-Id", "driver-1"))
                .andExpect(status().isCreated());

        // Se verifica qué chofer le llegó al servicio: con un stub condicional, un chofer distinto
        // devolvería null y el controller igual respondería 201, así que el test no podría fallar.
        verify(inspectionService).submit(eq("v-1"), any(), eq(new Driver("driver-1", "driver-1")));
    }

    @Test
    void sinHeaderDeChoferDevuelve400SinLlegarAlServicio() throws Exception {
        mvc.perform(post("/inspections/v-1").contentType(MediaType.APPLICATION_JSON).content(PRE_TRIP))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MISSING_DRIVER_HEADER"));
        verify(inspectionService, never()).submit(any(), any(), any());
    }

    @Test
    void unaInspeccionInvalidaDevuelve422ConElItemQueFalla() throws Exception {
        // El detalle de una inspección usa itemId (el ítem del checklist), no field.
        when(inspectionService.submit(eq("v-1"), any(), any())).thenThrow(new InspectionValidationException(
                "La inspección tiene errores", List.of(new ValidationErrorDetail("int-km", "Obligatorio"))));

        mvc.perform(post("/inspections/v-1").contentType(MediaType.APPLICATION_JSON).content(PRE_TRIP)
                        .header("X-Driver-Id", "driver-1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].itemId").value("int-km"));
    }

    @Test
    void unPreTripConElViajeYaAbiertoDevuelve409() throws Exception {
        when(inspectionService.submit(eq("v-1"), any(), any())).thenThrow(
                new VehicleStateConflictException("VEHICLE_ON_TRIP", "El vehículo ya tiene un pre-trip abierto."));

        mvc.perform(post("/inspections/v-1").contentType(MediaType.APPLICATION_JSON).content(PRE_TRIP)
                        .header("X-Driver-Id", "driver-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("VEHICLE_ON_TRIP"));
    }

    @Test
    void elListadoDeDefectosPasaLosFiltros() throws Exception {
        when(defectService.listDefects("v-1", "open")).thenReturn(List.of(new DefectDto("d-1", "blocking",
                "Neumático cortado", null, "2026-10-03T12:00:00Z", "v-1", "AB123CD", "open", "Carlos Gómez",
                "Corte lateral profundo")));

        mvc.perform(get("/defects").param("vehicleId", "v-1").param("status", "open"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].severity").value("blocking"))
                .andExpect(jsonPath("$[0].vehiclePlate").value("AB123CD"))
                .andExpect(jsonPath("$[0].details").value("Corte lateral profundo"));
    }
}
