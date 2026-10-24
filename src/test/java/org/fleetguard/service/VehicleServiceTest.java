package org.fleetguard.service;

import org.fleetguard.dto.CreateVehicleRequest;
import org.fleetguard.dto.UpdateVehicleRequest;
import org.fleetguard.dto.VehicleSummaryDto;
import org.fleetguard.entity.Trip;
import org.fleetguard.entity.Role;
import org.fleetguard.entity.TripStatus;
import org.fleetguard.entity.User;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.exception.VehicleNotFoundException;
import org.fleetguard.exception.VehicleStateConflictException;
import org.fleetguard.exception.VehicleValidationException;
import org.fleetguard.mapper.VehicleMapper;
import org.fleetguard.repository.DefectRepository;
import org.fleetguard.repository.TripRepository;
import org.fleetguard.repository.UserRepository;
import org.fleetguard.repository.VehicleMaintenanceAssignmentRepository;
import org.fleetguard.repository.VehicleRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VehicleServiceTest {

    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final TripRepository tripRepository = mock(TripRepository.class);
    private final VehicleMaintenanceAssignmentRepository assignmentRepository = mock(VehicleMaintenanceAssignmentRepository.class);
    private final DefectRepository defectRepository = mock(DefectRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final VehicleMapper vehicleMapper = new VehicleMapper();
    private final VehicleService service = new VehicleService(vehicleRepository, tripRepository, assignmentRepository,
            defectRepository, new UserNameService(userRepository), vehicleMapper, event -> { });

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

    private User userInDb(String username) throws Exception {
        User user = new User(username, "hash", Role.CHOFER);
        setId(user, UUID.randomUUID());
        when(userRepository.findAllById(List.of(user.getId()))).thenReturn(List.of(user));
        return user;
    }

    @Test
    void unVehiculoEnViajeDiceQuienLoAbrioYDesdeCuando() throws Exception {
        UUID id = UUID.randomUUID();
        Vehicle vehicle = new Vehicle("AB123CD", "Ford", "Cargo");
        setId(vehicle, id);
        User chofer = userInDb("chofer1");
        Instant startedAt = Instant.parse("2026-10-07T11:15:00Z");
        when(vehicleRepository.findById(id)).thenReturn(Optional.of(vehicle));
        when(tripRepository.findFirstByVehicle_IdAndStatus(id, TripStatus.OPEN))
                .thenReturn(Optional.of(new Trip(vehicle, startedAt, chofer.getId(), "chofer1")));

        VehicleSummaryDto result = service.getById(id.toString());

        assertEquals("on-trip", result.status());
        assertEquals("2026-10-07T11:15:00Z", result.currentTrip().startedAt());
        assertEquals("chofer1", result.currentTrip().openedBy().name());
        assertEquals(chofer.getId().toString(), result.currentTrip().openedBy().id());
    }

    @Test
    void siAlChoferLoRenombraronSeVeSuNombreActual() throws Exception {
        UUID id = UUID.randomUUID();
        Vehicle vehicle = new Vehicle("AB123CD", "Ford", "Cargo");
        setId(vehicle, id);
        User chofer = userInDb("Juan Pérez");
        when(vehicleRepository.findById(id)).thenReturn(Optional.of(vehicle));
        when(tripRepository.findFirstByVehicle_IdAndStatus(id, TripStatus.OPEN))
                .thenReturn(Optional.of(new Trip(vehicle, Instant.now(), chofer.getId(), "nombre.viejo")));

        assertEquals("Juan Pérez", service.getById(id.toString()).currentTrip().openedBy().name());
    }

    @Test
    void unViajeAnteriorSinChoferSeVeSinErrores() throws Exception {
        UUID id = UUID.randomUUID();
        Vehicle vehicle = new Vehicle("AB123CD", "Ford", "Cargo");
        setId(vehicle, id);
        when(vehicleRepository.findById(id)).thenReturn(Optional.of(vehicle));
        when(tripRepository.findFirstByVehicle_IdAndStatus(id, TripStatus.OPEN))
                .thenReturn(Optional.of(new Trip(vehicle, Instant.now())));

        VehicleSummaryDto result = service.getById(id.toString());

        assertEquals("on-trip", result.status());
        assertNull(result.currentTrip().openedBy());
    }

    @Test
    void unVehiculoDisponibleNoTieneViajeActual() throws Exception {
        UUID id = UUID.randomUUID();
        Vehicle vehicle = new Vehicle("AB123CD", "Ford", "Cargo");
        setId(vehicle, id);
        when(vehicleRepository.findById(id)).thenReturn(Optional.of(vehicle));
        when(tripRepository.findFirstByVehicle_IdAndStatus(id, TripStatus.OPEN)).thenReturn(Optional.empty());

        VehicleSummaryDto result = service.getById(id.toString());

        assertEquals("available", result.status());
        assertNull(result.currentTrip());
        assertFalse(result.blocked());
    }

    @Test
    void unDefectoBloqueanteAbiertoMarcaElVehiculoComoBloqueado() throws Exception {
        UUID id = UUID.randomUUID();
        Vehicle vehicle = new Vehicle("AB123CD", "Ford", "Cargo");
        setId(vehicle, id);
        when(vehicleRepository.findById(id)).thenReturn(Optional.of(vehicle));
        when(tripRepository.findFirstByVehicle_IdAndStatus(id, TripStatus.OPEN)).thenReturn(Optional.empty());
        when(defectRepository.existsOpenBlockingByVehicleId(id)).thenReturn(true);

        assertTrue(service.getById(id.toString()).blocked());
    }

    @Test
    void elListadoMarcaViajesYBloqueosSinConsultarVehiculoPorVehiculo() throws Exception {
        Vehicle enViaje = new Vehicle("AA111AA", "Ford", "Cargo");
        Vehicle bloqueado = new Vehicle("BB222BB", "Iveco", "Daily");
        Vehicle libre = new Vehicle("CC333CC", "Scania", "R450");
        setId(enViaje, UUID.randomUUID());
        setId(bloqueado, UUID.randomUUID());
        setId(libre, UUID.randomUUID());
        User chofer = userInDb("chofer1");
        when(vehicleRepository.findByActive(true)).thenReturn(List.of(libre, bloqueado, enViaje));
        when(tripRepository.findByStatusWithVehicle(TripStatus.OPEN))
                .thenReturn(List.of(new Trip(enViaje, Instant.now(), chofer.getId(), "chofer1")));
        when(defectRepository.findVehicleIdsWithOpenBlockingDefect()).thenReturn(List.of(bloqueado.getId()));

        List<VehicleSummaryDto> result = service.listVehicles(true);

        // CAM-15: por patente. CAM-89: el chofer ya no necesita pedir /api/defects para saber cuál está bloqueado.
        assertEquals(List.of("AA111AA", "BB222BB", "CC333CC"), result.stream().map(VehicleSummaryDto::plate).toList());
        assertEquals("on-trip", result.get(0).status());
        assertEquals("chofer1", result.get(0).currentTrip().openedBy().name());
        assertTrue(result.get(1).blocked());
        assertEquals("available", result.get(1).status());
        assertFalse(result.get(2).blocked());
        assertNull(result.get(2).currentTrip());
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
