package org.fleetguard.controller;

import org.fleetguard.auth.AuthenticatedUser;
import org.fleetguard.dto.DefectDto;
import org.fleetguard.dto.InspectionDto;
import org.fleetguard.dto.InspectionResultDto;
import org.fleetguard.dto.TripDto;
import org.fleetguard.dto.UserRefDto;
import org.fleetguard.dto.ValidationErrorDetail;
import org.fleetguard.entity.Role;
import org.fleetguard.exception.InspectionValidationException;
import org.fleetguard.exception.VehicleStateConflictException;
import org.fleetguard.service.DefectService;
import org.fleetguard.service.InspectionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Envío de inspecciones (CAM-11) y listado de defectos (CAM-13). Las inspecciones exigen el JWT
 * de un CHOFER (CAM-85): el chofer que le llega al servicio es el del token, no un dato del pedido.
 */
@WebMvcTest({InspectionController.class, DefectController.class})
class InspectionAndDefectControllerTest extends WebMvcTestBase {

    @MockBean
    private InspectionService inspectionService;

    @MockBean
    private DefectService defectService;

    private static final String PRE_TRIP = "{\"type\":\"pre-trip\",\"answers\":[]}";

    private static InspectionResultDto result() {
        InspectionDto inspection = new InspectionDto("i-1", "t-1", "v-1", "u-1", "pre-trip", "2026-10-03T12:00:00Z",
                12345.0, List.of(), null, false);
        return new InspectionResultDto(inspection, new TripDto("t-1", "v-1", "open", "2026-10-03T12:00:00Z", null,
                new UserRefDto("u-1", "chofer1"), null));
    }

    @Test
    void enviarUnaInspeccionDevuelve201ConElViajeAbiertoYQuienLoAbrio() throws Exception {
        when(inspectionService.submit(eq("v-1"), any(), any())).thenReturn(result());

        mvc.perform(post("/inspections/v-1").contentType(MediaType.APPLICATION_JSON).content(PRE_TRIP)
                        .header(HttpHeaders.AUTHORIZATION, loginAs("chofer1", Role.CHOFER)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.inspection.type").value("pre-trip"))
                .andExpect(jsonPath("$.trip.status").value("open"))
                .andExpect(jsonPath("$.trip.openedBy.name").value("chofer1"))
                .andExpect(jsonPath("$.trip.closedBy").doesNotExist());
    }

    @Test
    void elChoferDeLaInspeccionEsElDelToken() throws Exception {
        when(inspectionService.submit(eq("v-1"), any(), any())).thenReturn(result());

        // Los headers X-Driver-* de antes ya no cuentan: aunque vengan, manda el token.
        mvc.perform(post("/inspections/v-1").contentType(MediaType.APPLICATION_JSON).content(PRE_TRIP)
                        .header(HttpHeaders.AUTHORIZATION, loginAs("chofer1", Role.CHOFER))
                        .header("X-Driver-Id", "otro").header("X-Driver-Name", "Otro Chofer"))
                .andExpect(status().isCreated());

        ArgumentCaptor<AuthenticatedUser> driver = ArgumentCaptor.forClass(AuthenticatedUser.class);
        verify(inspectionService).submit(eq("v-1"), any(), driver.capture());
        assertEquals("chofer1", driver.getValue().username());
        assertEquals(Role.CHOFER, driver.getValue().role());
    }

    @Test
    void sinTokenDevuelve401SinLlegarAlServicio() throws Exception {
        mvc.perform(post("/inspections/v-1").contentType(MediaType.APPLICATION_JSON).content(PRE_TRIP))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        verify(inspectionService, never()).submit(any(), any(), any());
    }

    @Test
    void conTokenDeAdminODeTecnicoDevuelve403() throws Exception {
        mvc.perform(post("/inspections/v-1").contentType(MediaType.APPLICATION_JSON).content(PRE_TRIP)
                        .header(HttpHeaders.AUTHORIZATION, loginAs("admin", Role.ADMIN)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        mvc.perform(post("/inspections/v-1").contentType(MediaType.APPLICATION_JSON).content(PRE_TRIP)
                        .header(HttpHeaders.AUTHORIZATION, loginAs("tecnico", Role.TECNICO)))
                .andExpect(status().isForbidden());
        verify(inspectionService, never()).submit(any(), any(), any());
    }

    @Test
    void unaInspeccionInvalidaDevuelve422ConElItemQueFalla() throws Exception {
        // El detalle de una inspección usa itemId (el ítem del checklist), no field.
        when(inspectionService.submit(eq("v-1"), any(), any())).thenThrow(new InspectionValidationException(
                "La inspección tiene errores", List.of(new ValidationErrorDetail("int-km", "Obligatorio"))));

        mvc.perform(post("/inspections/v-1").contentType(MediaType.APPLICATION_JSON).content(PRE_TRIP)
                        .header(HttpHeaders.AUTHORIZATION, loginAs("chofer1", Role.CHOFER)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].itemId").value("int-km"));
    }

    @Test
    void unPreTripConElViajeYaAbiertoDevuelve409() throws Exception {
        when(inspectionService.submit(eq("v-1"), any(), any())).thenThrow(
                new VehicleStateConflictException("VEHICLE_ON_TRIP", "El vehículo ya tiene un pre-trip abierto."));

        mvc.perform(post("/inspections/v-1").contentType(MediaType.APPLICATION_JSON).content(PRE_TRIP)
                        .header(HttpHeaders.AUTHORIZATION, loginAs("chofer1", Role.CHOFER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("VEHICLE_ON_TRIP"));
    }

    @Test
    void unPreTripSobreUnVehiculoBloqueadoDevuelve409() throws Exception {
        when(inspectionService.submit(eq("v-1"), any(), any())).thenThrow(
                new VehicleStateConflictException("VEHICLE_BLOCKED", "El vehículo tiene un defecto bloqueante."));

        mvc.perform(post("/inspections/v-1").contentType(MediaType.APPLICATION_JSON).content(PRE_TRIP)
                        .header(HttpHeaders.AUTHORIZATION, loginAs("chofer1", Role.CHOFER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("VEHICLE_BLOCKED"));
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
