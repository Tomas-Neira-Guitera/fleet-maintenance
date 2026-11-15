package org.fleetguard.service;

import org.fleetguard.dto.BlockReasonDto;
import org.fleetguard.dto.CreateVehicleRequest;
import org.fleetguard.dto.UpdateVehicleRequest;
import org.fleetguard.dto.VehicleSummaryDto;
import org.fleetguard.entity.Trip;
import org.fleetguard.entity.Role;
import org.fleetguard.entity.TripStatus;
import org.fleetguard.entity.User;
import org.fleetguard.entity.Tire;
import org.fleetguard.entity.TireMounting;
import org.fleetguard.entity.TirePosition;
import org.fleetguard.entity.TrailerCoupling;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.VehicleType;
import org.fleetguard.repository.TireMountingRepository;
import org.fleetguard.repository.TrailerCouplingRepository;
import org.fleetguard.exception.VehicleNotFoundException;
import org.fleetguard.exception.VehicleStateConflictException;
import org.fleetguard.exception.VehicleValidationException;
import org.fleetguard.mapper.VehicleMapper;
import org.fleetguard.repository.TripRepository;
import org.fleetguard.repository.UserRepository;
import org.fleetguard.repository.VehicleMaintenanceAssignmentRepository;
import org.fleetguard.repository.VehicleRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VehicleServiceTest {

    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final TripRepository tripRepository = mock(TripRepository.class);
    private final VehicleMaintenanceAssignmentRepository assignmentRepository = mock(VehicleMaintenanceAssignmentRepository.class);
    private final VehicleAvailabilityService availabilityService = mock(VehicleAvailabilityService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final VehicleMapper vehicleMapper = new VehicleMapper();
    private final ScheduledToday scheduledToday = mock(ScheduledToday.class);
    private final ChecklistTemplateService checklistTemplateService = mock(ChecklistTemplateService.class);
    private final TireMountingRepository tireMountingRepository = mock(TireMountingRepository.class);
    private final TrailerCouplingRepository couplingRepository = mock(TrailerCouplingRepository.class);
    private final VehicleService service = new VehicleService(vehicleRepository, tripRepository, assignmentRepository,
            availabilityService, new UserNameService(userRepository), vehicleMapper, mock(KmPerDayEstimator.class),
            event -> { }, scheduledToday, checklistTemplateService, tireMountingRepository, couplingRepository);

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
        when(availabilityService.reasons(id)).thenReturn(List.of(
                new BlockReasonDto(BlockReasonDto.BLOCKING_DEFECT, "Defecto bloqueante: Sin frenos")));

        VehicleSummaryDto result = service.getById(id.toString());
        assertTrue(result.blocked());
        assertEquals("Defecto bloqueante: Sin frenos", result.blockReasons().get(0).message());
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
        when(availabilityService.reasonsFor(any())).thenReturn(Map.of(bloqueado.getId(),
                List.of(new BlockReasonDto(BlockReasonDto.EXPIRED_DOCUMENT, "Seguro: venció el 01/10/2026"))));

        List<VehicleSummaryDto> result = service.listVehicles(true);

        // CAM-15: por patente. CAM-89: el chofer ya no necesita pedir /api/defects para saber cuál está bloqueado.
        assertEquals(List.of("AA111AA", "BB222BB", "CC333CC"), result.stream().map(VehicleSummaryDto::plate).toList());
        assertEquals("on-trip", result.get(0).status());
        assertEquals("chofer1", result.get(0).currentTrip().openedBy().name());
        assertTrue(result.get(1).blocked());
        assertEquals("available", result.get(1).status());
        assertEquals("Seguro: venció el 01/10/2026", result.get(1).blockReasons().get(0).message());
        assertFalse(result.get(2).blocked());
        assertEquals(List.of(), result.get(2).blockReasons());
        assertNull(result.get(2).currentTrip());
    }

    @Test
    void elListadoTraeElTurnoDeHoyDeTodaLaFlotaEnUnaSolaConsulta() throws Exception {
        Vehicle conTurno = new Vehicle("AA111AA", "Ford", "Cargo");
        Vehicle sinTurno = new Vehicle("BB222BB", "Iveco", "Daily");
        setId(conTurno, UUID.randomUUID());
        setId(sinTurno, UUID.randomUUID());
        when(vehicleRepository.findByActive(true)).thenReturn(List.of(conTurno, sinTurno));
        Instant turno = Instant.parse("2026-11-12T18:00:00Z");
        when(scheduledToday.forAll()).thenReturn(Map.of(conTurno.getId(), turno));

        List<VehicleSummaryDto> result = service.listVehicles(true);

        assertEquals("2026-11-12T18:00:00Z", result.get(0).scheduledToday());
        assertNull(result.get(1).scheduledToday());
        org.mockito.Mockito.verify(scheduledToday, org.mockito.Mockito.never()).forVehicle(any());
    }

    @Test
    void elDetalleTraeElTurnoDeHoy() throws Exception {
        Vehicle vehicle = new Vehicle("AA111AA", "Ford", "Cargo");
        UUID id = UUID.randomUUID();
        setId(vehicle, id);
        when(vehicleRepository.findById(id)).thenReturn(Optional.of(vehicle));
        when(scheduledToday.forVehicle(id)).thenReturn(Instant.parse("2026-11-12T13:30:00Z"));

        assertEquals("2026-11-12T13:30:00Z", service.getById(id.toString()).scheduledToday());
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
        // "furgon" es un alias del catálogo: se guarda como utilitario.
        assertEquals("utilitario", result.vehicleType());
        assertEquals(2022, result.year());
        assertEquals("CHASSIS-1", result.chassisNumber());
        assertEquals(5000L, result.odometerKm());
        assertEquals(true, result.active());
    }

    // CAM-157: el tipo es del catálogo y define las cubiertas.
    @Test
    void elTipoEsObligatorioYDelCatalogo() {
        VehicleValidationException missing = assertThrows(VehicleValidationException.class,
                () -> service.create(new CreateVehicleRequest("AB123CD", "Iveco", "Daily", null, null, null, null)));
        assertEquals("vehicleType", missing.getDetails().get(0).field());

        VehicleValidationException unknown = assertThrows(VehicleValidationException.class,
                () -> service.create(new CreateVehicleRequest("AB123CD", "Iveco", "Daily", "colectivo", null, null, null)));
        assertEquals("vehicleType", unknown.getDetails().get(0).field());
    }

    @Test
    void noSeCambiaElTipoSiQuedanCubiertasEnPosicionesQueElNuevoNoTiene() throws Exception {
        Vehicle truck = new Vehicle("AB123CD", "Iveco", "Tector");
        truck.setVehicleType(VehicleType.CAMION_6X4);
        UUID id = UUID.randomUUID();
        setId(truck, id);
        when(vehicleRepository.findById(id)).thenReturn(Optional.of(truck));
        Tire tire = new Tire(null, "Fate", null, "295/80 R22.5", java.time.LocalDate.parse("2026-01-01"),
                java.math.BigDecimal.TEN);
        when(tireMountingRepository.findByVehicle_IdAndRemovedAtIsNull(id)).thenReturn(List.of(
                new TireMounting(tire, truck, TirePosition.E3_IZQ_EXT, java.time.Instant.now(), 0)));

        VehicleStateConflictException ex = assertThrows(VehicleStateConflictException.class,
                () -> service.update(id.toString(), new UpdateVehicleRequest(null, null, null, "camion_4x2", null, null, null)));

        assertEquals("TIRES_ON_REMOVED_POSITIONS", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Eje 3 izquierda exterior"));
    }

    @Test
    void noSeCambiaElTipoMientrasEstaEnganchado() throws Exception {
        Vehicle tractor = new Vehicle("AB123CD", "Scania", "R450");
        tractor.setVehicleType(VehicleType.TRACTOR_6X4);
        UUID id = UUID.randomUUID();
        setId(tractor, id);
        when(vehicleRepository.findById(id)).thenReturn(Optional.of(tractor));
        when(couplingRepository.findFirstByTowing_IdAndUncoupledAtIsNull(id)).thenReturn(Optional.of(
                new TrailerCoupling(new Vehicle("ZZ999ZZ", "Helvética", "Tri-eje"), tractor, java.time.Instant.now(), "admin")));

        VehicleStateConflictException ex = assertThrows(VehicleStateConflictException.class,
                () -> service.update(id.toString(), new UpdateVehicleRequest(null, null, null, "tractor_4x2", null, null, null)));

        assertEquals("VEHICLE_COUPLED", ex.getErrorCode());
    }

    // CAM-150: la plantilla elegida en el alta se copia al vehículo nuevo.
    @Test
    void altaConPlantillaCopiaSusControles() throws Exception {
        stubSaveReturnsSameEntityWithId();
        String templateId = UUID.randomUUID().toString();

        VehicleSummaryDto result = service.create(new CreateVehicleRequest("AB123CD", "Iveco", "Daily", "utilitario", null,
                null, null, templateId));

        verify(checklistTemplateService).requireExists(templateId, "checklistTemplateId");
        verify(checklistTemplateService).applyToNewVehicle(eq(templateId), eq(UUID.fromString(result.id())));
    }

    @Test
    void altaConPlantillaInexistenteNoCreaElVehiculo() {
        String templateId = UUID.randomUUID().toString();
        doThrow(new VehicleValidationException("Datos inválidos", List.of()))
                .when(checklistTemplateService).requireExists(templateId, "checklistTemplateId");

        assertThrows(VehicleValidationException.class, () -> service.create(new CreateVehicleRequest("AB123CD",
                "Iveco", "Daily", "utilitario", null, null, null, templateId)));
        verify(vehicleRepository, never()).save(any(Vehicle.class));
    }

    @Test
    void altaConPatenteDuplicadaLanza409() {
        when(vehicleRepository.existsByPlateIgnoreCase("AB123CD")).thenReturn(true);

        VehicleStateConflictException ex = assertThrows(VehicleStateConflictException.class,
                () -> service.create(new CreateVehicleRequest("AB123CD", "Iveco", "Daily", "utilitario", null, null, null)));
        assertEquals("DUPLICATE_PLATE", ex.getErrorCode());
    }

    @Test
    void altaConAñoFueraDeRangoLanza422() {
        VehicleValidationException ex = assertThrows(VehicleValidationException.class,
                () -> service.create(new CreateVehicleRequest("AC456EF", "Iveco", "Daily", "utilitario", 1900, null, null)));
        assertEquals(1, ex.getDetails().size());
        assertEquals("year", ex.getDetails().get(0).field());
    }

    @Test
    void ediciónParcialSoloCambiaLosCamposEnviados() throws Exception {
        Vehicle vehicle = new Vehicle("AD789GH", "Ford", "Cargo 1723");
        vehicle.setVehicleType(VehicleType.CAMION_4X2);
        UUID id = UUID.randomUUID();
        setId(vehicle, id);
        when(vehicleRepository.findById(id)).thenReturn(Optional.of(vehicle));
        stubSaveReturnsSameEntityWithId();

        VehicleSummaryDto result = service.update(id.toString(), new UpdateVehicleRequest(null, "Ford", null, null, 2019, null, null));

        assertEquals("AD789GH", result.plate());
        assertEquals("Cargo 1723", result.model());
        assertEquals("camion_4x2", result.vehicleType());
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
