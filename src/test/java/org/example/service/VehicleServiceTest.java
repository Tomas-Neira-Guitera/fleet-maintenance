package org.example.service;

import org.example.dto.CreateVehicleRequest;
import org.example.dto.UpdateVehicleRequest;
import org.example.dto.VehicleSummaryDto;
import org.example.entity.Trip;
import org.example.entity.TripStatus;
import org.example.entity.Vehicle;
import org.example.exception.VehicleNotFoundException;
import org.example.exception.VehicleStateConflictException;
import org.example.exception.VehicleValidationException;
import org.example.mapper.VehicleMapper;
import org.example.repository.TripRepository;
import org.example.repository.VehicleMaintenanceAssignmentRepository;
import org.example.repository.VehicleRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VehicleServiceTest {

    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final TripRepository tripRepository = mock(TripRepository.class);
    private final VehicleMaintenanceAssignmentRepository assignmentRepository = mock(VehicleMaintenanceAssignmentRepository.class);
    private final VehicleMapper vehicleMapper = new VehicleMapper();
    private final VehicleService service = new VehicleService(vehicleRepository, tripRepository, assignmentRepository, vehicleMapper);

    /** El id es @GeneratedValue; en el test se setea por reflexión, sin persistencia real (mismo patrón que DefectServiceTest). */
    private static void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    private void stubSaveReturnsSameEntityWithId() {
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(invocation -> {
            Vehicle v = invocation.getArgument(0);
            if (v.getId() == null) {
                setId(v, UUID.randomUUID());
            }
            return v;
        });
    }

    @Test
    void getByIdDevuelveElVehiculoIncluidoUnoDadoDeBaja() throws Exception {
        UUID id = UUID.randomUUID();
        Vehicle vehicle = new Vehicle("AB123CD", "Ford", "Cargo");
        setId(vehicle, id);
        vehicle.setActive(false);
        when(vehicleRepository.findById(id)).thenReturn(Optional.of(vehicle));
        when(tripRepository.findFirstByVehicle_IdAndStatus(id, TripStatus.OPEN)).thenReturn(Optional.empty());

        VehicleSummaryDto result = service.getById(id.toString());

        assertEquals("AB123CD", result.plate());
        assertFalse(result.active());
    }

    @Test
    void getByIdConIdInexistenteOMalformadoLanzaNotFound() {
        UUID id = UUID.randomUUID();
        when(vehicleRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(VehicleNotFoundException.class, () -> service.getById(id.toString()));
        assertThrows(VehicleNotFoundException.class, () -> service.getById("no-es-un-uuid"));
    }

    @Test
    void altaValidaCreaElVehiculo() throws Exception {
        stubSaveReturnsSameEntityWithId();
        when(vehicleRepository.existsByPlateIgnoreCase("AB123CD")).thenReturn(false);

        VehicleSummaryDto result = service.create(
                new CreateVehicleRequest("AB123CD", "Mercedes-Benz", "Sprinter", "furgon", 2022, "CHASSIS-1", 5000L));

        assertEquals("AB123CD", result.plate());
        assertEquals("Mercedes-Benz", result.brand());
        assertEquals("furgon", result.vehicleType());
        assertEquals(2022, result.year());
        assertEquals("CHASSIS-1", result.chassisNumber());
        assertEquals(5000L, result.odometerKm());
        assertEquals(true, result.active());
    }

    @Test
    void altaConPatenteDuplicadaLanza409() {
        when(vehicleRepository.existsByPlateIgnoreCase("AB123CD")).thenReturn(true);

        VehicleStateConflictException ex = assertThrows(VehicleStateConflictException.class,
                () -> service.create(new CreateVehicleRequest("AB123CD", "Iveco", "Daily", null, null, null, null)));
        assertEquals("DUPLICATE_PLATE", ex.getErrorCode());
    }

    @Test
    void altaConAñoFueraDeRangoLanza422() {
        VehicleValidationException ex = assertThrows(VehicleValidationException.class,
                () -> service.create(new CreateVehicleRequest("AC456EF", "Iveco", "Daily", null, 1900, null, null)));
        assertEquals(1, ex.getDetails().size());
        assertEquals("year", ex.getDetails().get(0).field());
    }

    @Test
    void ediciónParcialSoloCambiaLosCamposEnviados() throws Exception {
        Vehicle vehicle = new Vehicle("AD789GH", "Ford", "Cargo 1723");
        vehicle.setVehicleType("camion");
        UUID id = UUID.randomUUID();
        setId(vehicle, id);
        when(vehicleRepository.findById(id)).thenReturn(Optional.of(vehicle));
        stubSaveReturnsSameEntityWithId();

        VehicleSummaryDto result = service.update(id.toString(), new UpdateVehicleRequest(null, "Ford", null, null, 2019, null, null));

        assertEquals("AD789GH", result.plate());
        assertEquals("Cargo 1723", result.model());
        assertEquals("camion", result.vehicleType());
        assertEquals(2019, result.year());
    }

    @Test
    void bajaNormalDesactivaElVehiculo() throws Exception {
        Vehicle vehicle = new Vehicle("AE234JK", "Toyota", "Hilux");
        UUID id = UUID.randomUUID();
        setId(vehicle, id);
        when(vehicleRepository.findById(id)).thenReturn(Optional.of(vehicle));
        when(tripRepository.findFirstByVehicle_IdAndStatus(id, TripStatus.OPEN)).thenReturn(Optional.empty());
        stubSaveReturnsSameEntityWithId();

        service.deactivate(id.toString());

        assertFalse(vehicle.isActive());
    }

    @Test
    void bajaConViajeAbiertoLanza409() throws Exception {
        Vehicle vehicle = new Vehicle("AF567LM", "Scania", "R450");
        UUID id = UUID.randomUUID();
        setId(vehicle, id);
        Trip openTrip = mock(Trip.class);
        when(vehicleRepository.findById(id)).thenReturn(Optional.of(vehicle));
        when(tripRepository.findFirstByVehicle_IdAndStatus(id, TripStatus.OPEN)).thenReturn(Optional.of(openTrip));

        VehicleStateConflictException ex = assertThrows(VehicleStateConflictException.class,
                () -> service.deactivate(id.toString()));
        assertEquals("VEHICLE_ON_TRIP", ex.getErrorCode());
        assertEquals(true, vehicle.isActive());
    }
}
