package org.fleetguard.controller;

import org.fleetguard.dto.VehicleCostsDto;
import org.fleetguard.entity.Role;
import org.fleetguard.exception.VehicleNotFoundException;
import org.fleetguard.service.VehicleCostsService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;

import java.math.BigDecimal;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/vehicles/{id}/costs (CAM-105): solo lectura y solo para el admin. */
@WebMvcTest(VehicleCostsController.class)
class VehicleCostsControllerTest extends WebMvcTestBase {

    @MockBean
    private VehicleCostsService service;

    @Test
    void elAdminVeLosCostosDelVehiculo() throws Exception {
        when(service.getCosts("v-1")).thenReturn(new VehicleCostsDto(new BigDecimal("300000.00"),
                new BigDecimal("100000.00"), 3, 60000, 50000L, 10000L, new BigDecimal("30.00")));

        mvc.perform(get("/vehicles/v-1/costs").header(HttpHeaders.AUTHORIZATION, loginAs("admin", Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(300000.00))
                .andExpect(jsonPath("$.last12MonthsAmount").value(100000.00))
                .andExpect(jsonPath("$.expenseCount").value(3))
                .andExpect(jsonPath("$.kmDriven").value(10000))
                .andExpect(jsonPath("$.costPerKm").value(30.00));
    }

    @Test
    void sinKmSuficientesElCostoPorKmVieneEnNull() throws Exception {
        when(service.getCosts("v-1")).thenReturn(new VehicleCostsDto(new BigDecimal("1000.00"), BigDecimal.ZERO, 1,
                0, null, null, null));

        mvc.perform(get("/vehicles/v-1/costs").header(HttpHeaders.AUTHORIZATION, loginAs("admin", Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstKnownKm").isEmpty())
                .andExpect(jsonPath("$.kmDriven").isEmpty())
                .andExpect(jsonPath("$.costPerKm").isEmpty());
    }

    @Test
    void sinTokenDevuelve401YConOtroRol403() throws Exception {
        mvc.perform(get("/vehicles/v-1/costs"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/vehicles/v-1/costs").header(HttpHeaders.AUTHORIZATION, loginAs("chofer1", Role.CHOFER)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/vehicles/v-1/costs").header(HttpHeaders.AUTHORIZATION, loginAs("tecnico", Role.TECNICO)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void unVehiculoQueNoExisteDevuelve404() throws Exception {
        when(service.getCosts("v-x")).thenThrow(new VehicleNotFoundException("v-x"));

        mvc.perform(get("/vehicles/v-x/costs").header(HttpHeaders.AUTHORIZATION, loginAs("admin", Role.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("VEHICLE_NOT_FOUND"));
    }
}
