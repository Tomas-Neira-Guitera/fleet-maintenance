package org.fleetguard.service;

import org.fleetguard.dto.CreateVehicleRequest;
import org.fleetguard.dto.ImportVehiclesRequest;
import org.fleetguard.dto.VehicleImportResultDto;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.exception.VehicleValidationException;
import org.fleetguard.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CAM-154 -- importar la flota desde un archivo. */
class VehicleImportServiceTest {

    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final VehicleImportService service = new VehicleImportService(vehicleRepository);

    private static CreateVehicleRequest row(String plate, String brand, String model, Integer year, Long km) {
        return new CreateVehicleRequest(plate, brand, model, "Camión", year, null, km);
    }

    private static ImportVehiclesRequest request(CreateVehicleRequest... rows) {
        return new ImportVehiclesRequest(List.of(rows));
    }

    @Test
    void laVistaPreviaValidaCadaFilaSinGuardarNada() {
        VehicleImportResultDto result = service.importVehicles(request(
                row("AB123CD", "Ford", "Cargo", 2019, 120000L),
                row("", "Fiat", "Ducato", null, null),
                row("AC456EF", "Iveco", "Daily", 1950, -5L)), true);

        assertEquals(3, result.total());
        assertEquals(1, result.valid());
        assertEquals(0, result.created());
        assertTrue(result.rows().get(0).valid());
        assertEquals("plate", result.rows().get(1).errors().get(0).field());
        assertEquals(List.of("year", "odometerKm"),
                result.rows().get(2).errors().stream().map(e -> e.field()).toList());
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void alConfirmarCreaSoloLasFilasValidas() {
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VehicleImportResultDto result = service.importVehicles(request(
                row("AB123CD", "Ford", "Cargo", 2019, 120000L),
                row("AC456EF", "", "Daily", null, null),
                row("AD789GH", "Scania", "R450", 2021, null)), false);

        assertEquals(2, result.created());
        ArgumentCaptor<Vehicle> saved = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository, times(2)).save(saved.capture());
        assertEquals(List.of("AB123CD", "AD789GH"), saved.getAllValues().stream().map(Vehicle::getPlate).toList());
        assertEquals(120000L, saved.getAllValues().get(0).getOdometerKm());
    }

    @Test
    void unaPatenteQueYaExisteNoSeDuplica() {
        when(vehicleRepository.existsByPlateIgnoreCase("AB123CD")).thenReturn(true);

        VehicleImportResultDto result = service.importVehicles(request(row("ab 123 cd", "Ford", "Cargo", null, null)),
                false);

        assertFalse(result.rows().get(0).valid());
        assertEquals("AB123CD", result.rows().get(0).plate());
        assertTrue(result.rows().get(0).errors().get(0).message().contains("Ya existe"));
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void unaPatenteRepetidaEnElArchivoSeMarcaEnLaSegundaFila() {
        VehicleImportResultDto result = service.importVehicles(request(
                row("AB123CD", "Ford", "Cargo", null, null),
                row("ab123cd", "Fiat", "Ducato", null, null)), true);

        assertTrue(result.rows().get(0).valid());
        assertFalse(result.rows().get(1).valid());
        assertEquals("La patente se repite en el archivo (fila 1)", result.rows().get(1).errors().get(0).message());
    }

    @Test
    void unArchivoVacioODemasiadoGrandeSeRechaza() {
        assertThrows(VehicleValidationException.class, () -> service.importVehicles(request(), true));
        List<CreateVehicleRequest> tooMany = new ArrayList<>(Collections.nCopies(VehicleImportService.MAX_ROWS + 1,
                row("AB123CD", "Ford", "Cargo", null, null)));
        assertThrows(VehicleValidationException.class,
                () -> service.importVehicles(new ImportVehiclesRequest(tooMany), true));
    }
}
