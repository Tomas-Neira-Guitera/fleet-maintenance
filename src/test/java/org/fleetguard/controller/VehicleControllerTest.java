package org.fleetguard.controller;

import org.fleetguard.dto.CreateVehicleRequest;
import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.dto.FleetStatusRowDto;
import org.fleetguard.dto.OdometerResultDto;
import org.fleetguard.dto.PagedResponse;
import org.fleetguard.dto.VehicleSummaryDto;
import org.fleetguard.exception.MaintenanceConflictException;
import org.fleetguard.exception.VehicleNotFoundException;
import org.fleetguard.exception.VehicleStateConflictException;
import org.fleetguard.exception.VehicleValidationException;
import org.fleetguard.service.VehicleHistoryService;
import org.fleetguard.service.VehicleService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /api/vehicles: listado, estado de flota (CAM-40), ABM (CAM-25), kilometraje e historial (CAM-22). */
@WebMvcTest(VehicleController.class)
class VehicleControllerTest extends WebMvcTestBase {

    @MockBean
    private VehicleService vehicleService;

    @MockBean
    private VehicleHistoryService vehicleHistoryService;

    private static VehicleSummaryDto summary() {
        return new VehicleSummaryDto("v-1", "AB123CD", "Ford", "Cargo", "available", "Camión", 2020, null, 50000, true);
    }

    @Test
    void elListadoEsUnArrayDeVehiculosConSuEstado() throws Exception {
        when(vehicleService.listVehicles(true)).thenReturn(List.of(summary()));

        mvc.perform(get("/vehicles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].plate").value("AB123CD"))
                .andExpect(jsonPath("$[0].status").value("available"))
                .andExpect(jsonPath("$[0].odometerKm").value(50000));
    }

    @Test
    void elParametroViewFleetStatusDevuelveLaVistaPaginadaDeFlota() throws Exception {
        FleetStatusRowDto row = new FleetStatusRowDto("v-1", "AB123CD", "Ford", "Cargo", null, 50000, 75, "vencido", null);
        when(vehicleService.getFleetStatus(2, 5, "vencido", true))
                .thenReturn(new PagedResponse<>(2, 5, 6, List.of(row)));

        // Mismo path que el listado, distinto recurso según el query param (ver openapi.yaml).
        mvc.perform(get("/vehicles").param("view", "fleet-status").param("page", "2")
                        .param("pageSize", "5").param("status", "vencido"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(6))
                .andExpect(jsonPath("$.items[0].healthScore").value(75))
                .andExpect(jsonPath("$.items[0].status").value("vencido"));
    }

    @Test
    void altaDevuelve201ConElVehiculoCreado() throws Exception {
        when(vehicleService.create(any(CreateVehicleRequest.class))).thenReturn(summary());

        mvc.perform(post("/vehicles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"AB123CD\",\"brand\":\"Ford\",\"model\":\"Cargo\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("v-1"));
    }

    @Test
    void unaPatenteRepetidaDevuelve409ConSuCodigo() throws Exception {
        when(vehicleService.create(any())).thenThrow(
                new VehicleStateConflictException("DUPLICATE_PLATE", "Ya existe un vehículo con la patente AB123CD"));

        mvc.perform(post("/vehicles").contentType(MediaType.APPLICATION_JSON).content("{\"plate\":\"AB123CD\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_PLATE"))
                .andExpect(jsonPath("$.message").value("Ya existe un vehículo con la patente AB123CD"));
    }

    @Test
    void datosInvalidosDevuelven422ConElDetallePorCampo() throws Exception {
        when(vehicleService.create(any())).thenThrow(new VehicleValidationException("Datos inválidos para crear el vehículo",
                List.of(new FieldValidationErrorDetail("brand", "La marca es obligatoria"))));

        mvc.perform(post("/vehicles").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("brand"));
    }

    @Test
    void unVehiculoInexistenteDevuelve404() throws Exception {
        when(vehicleService.getById("v-x")).thenThrow(new VehicleNotFoundException("v-x"));

        mvc.perform(get("/vehicles/v-x"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("VEHICLE_NOT_FOUND"));
    }

    @Test
    void darDeBajaUnVehiculoEnViajeDevuelve409() throws Exception {
        doThrow(new VehicleStateConflictException("VEHICLE_ON_TRIP", "El vehículo tiene un viaje abierto"))
                .when(vehicleService).deactivate("v-1");

        mvc.perform(delete("/vehicles/v-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("VEHICLE_ON_TRIP"));
    }

    @Test
    void cargarKilometrajeYSuRegresionDevuelven200Y409() throws Exception {
        when(vehicleService.updateOdometer("v-1", 51000)).thenReturn(new OdometerResultDto("v-1", 51000, "2026-10-03T00:00:00Z"));
        when(vehicleService.updateOdometer("v-1", 100))
                .thenThrow(new MaintenanceConflictException("ODOMETER_REGRESSION", "El kilometraje no puede ser menor"));

        mvc.perform(patch("/vehicles/v-1/odometer").contentType(MediaType.APPLICATION_JSON).content("{\"odometerKm\":51000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.odometerKm").value(51000));
        mvc.perform(patch("/vehicles/v-1/odometer").contentType(MediaType.APPLICATION_JSON).content("{\"odometerKm\":100}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ODOMETER_REGRESSION"));
    }

    @Test
    void unKilometrajeQueNoEsNumeroDevuelve400YNo500() throws Exception {
        mvc.perform(patch("/vehicles/v-1/odometer").contentType(MediaType.APPLICATION_JSON).content("{\"odometerKm\":\"mucho\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"));
        verify(vehicleService, org.mockito.Mockito.never()).updateOdometer(any(), anyLong());
    }
}
