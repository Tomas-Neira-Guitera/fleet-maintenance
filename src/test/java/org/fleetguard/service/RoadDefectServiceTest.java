package org.fleetguard.service;

import org.fleetguard.auth.AuthenticatedUser;
import org.fleetguard.dto.DefectDetailDto;
import org.fleetguard.dto.DefectDto;
import org.fleetguard.dto.RoadDefectRequest;
import org.fleetguard.entity.Defect;
import org.fleetguard.entity.DefectSeverity;
import org.fleetguard.entity.Inspection;
import org.fleetguard.entity.InspectionAnswer;
import org.fleetguard.entity.InspectionType;
import org.fleetguard.entity.Role;
import org.fleetguard.entity.Trip;
import org.fleetguard.entity.TripStatus;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.checklist.ChecklistCatalog;
import org.fleetguard.entity.checklist.ChecklistItemDef;
import org.fleetguard.entity.checklist.ChecklistItemType;
import org.fleetguard.entity.checklist.ChecklistSection;
import org.fleetguard.event.BlockingDefectReportedEvent;
import org.fleetguard.event.DefectsReportedEvent;
import org.fleetguard.exception.InspectionValidationException;
import org.fleetguard.exception.VehicleNotFoundException;
import org.fleetguard.exception.VehicleStateConflictException;
import org.fleetguard.mapper.DefectMapper;
import org.fleetguard.repository.InspectionRepository;
import org.fleetguard.repository.TripRepository;
import org.fleetguard.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CAM-139: el chofer reporta un defecto durante su viaje, sin cerrarlo. */
class RoadDefectServiceTest {

    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final TripRepository tripRepository = mock(TripRepository.class);
    private final InspectionRepository inspectionRepository = mock(InspectionRepository.class);
    private final VehicleChecklistService checklistService = mock(VehicleChecklistService.class);
    private final VehicleAvailabilityService availabilityService = mock(VehicleAvailabilityService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final RoadDefectService service = new RoadDefectService(vehicleRepository, tripRepository,
            inspectionRepository, checklistService, availabilityService, new DefectMapper(), eventPublisher);

    private final AuthenticatedUser driver = new AuthenticatedUser(UUID.randomUUID(), "chofer1", Role.CHOFER);
    private Vehicle vehicle;

    private static void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    @BeforeEach
    void setUp() throws Exception {
        vehicle = new Vehicle("AB123CD", "Ford", "Cargo");
        setId(vehicle, UUID.randomUUID());
        vehicle.setOdometerKm(50_000L);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        List<ChecklistItemDef> checklist = new ArrayList<>(ChecklistCatalog.preTripItems());
        checklist.add(new ChecklistItemDef("custom-grua", "Grúa", ChecklistItemType.CHECK, ChecklistSection.EXTERIOR));
        when(checklistService.resolve(vehicle.getId(), InspectionType.PRE_TRIP)).thenReturn(checklist);
        // Los ids son @GeneratedValue: el mock de save los asigna como lo haría JPA, también al defecto.
        when(inspectionRepository.save(any(Inspection.class))).thenAnswer(invocation -> {
            Inspection inspection = invocation.getArgument(0);
            setId(inspection, UUID.randomUUID());
            for (InspectionAnswer answer : inspection.getAnswers()) {
                setId(answer, UUID.randomUUID());
                if (answer.getDefect() != null) {
                    setId(answer.getDefect(), UUID.randomUUID());
                }
            }
            return inspection;
        });
    }

    private Trip openTrip(UUID openedBy) throws Exception {
        Trip trip = new Trip(vehicle, Instant.now().minusSeconds(3600), openedBy, "alguien");
        setId(trip, UUID.randomUUID());
        when(tripRepository.findFirstByVehicle_IdAndStatus(vehicle.getId(), TripStatus.OPEN)).thenReturn(Optional.of(trip));
        return trip;
    }

    private static RoadDefectRequest request(String itemId, String severity, String photoUrl) {
        return new RoadDefectRequest(itemId, new DefectDetailDto(severity, "Sin luz de freno", photoUrl,
                "  Se apagó en la ruta.  "));
    }

    private Inspection savedInspection() {
        ArgumentCaptor<Inspection> captor = ArgumentCaptor.forClass(Inspection.class);
        verify(inspectionRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void reportaUnNoBloqueante_quedaEnElViajeAbiertoSinKmYSinAvisar() throws Exception {
        Trip trip = openTrip(driver.id());

        DefectDto result = service.report(vehicle.getId().toString(), request("ext-luces", "non-blocking", null), driver);

        Inspection inspection = savedInspection();
        assertEquals(InspectionType.ON_ROAD, inspection.getType());
        assertEquals(trip, inspection.getTrip());
        assertEquals(driver.id().toString(), inspection.getDriverId());
        assertNull(inspection.getOdometerKm());
        assertFalse(inspection.isHasBlockingDefect());
        assertEquals(1, inspection.getAnswers().size());
        InspectionAnswer answer = inspection.getAnswers().get(0);
        assertEquals("ext-luces", answer.getItemId());
        assertEquals("Luces", answer.getItemLabel());
        Defect defect = answer.getDefect();
        assertEquals(DefectSeverity.NON_BLOCKING, defect.getSeverity());
        assertEquals("Se apagó en la ruta.", defect.getDetails());

        // El viaje sigue abierto y el km del vehículo no se mueve.
        assertEquals(TripStatus.OPEN, trip.getStatus());
        assertEquals(50_000L, vehicle.getOdometerKm());
        verify(tripRepository, never()).save(any());

        assertEquals("on-road", result.source());
        assertEquals("chofer1", result.reportedBy());
        assertEquals(vehicle.getPlate(), result.vehiclePlate());

        verify(eventPublisher, never()).publishEvent(any(BlockingDefectReportedEvent.class));
        verify(availabilityService, never()).sync(any(Vehicle.class), any());
        ArgumentCaptor<DefectsReportedEvent> reported = ArgumentCaptor.forClass(DefectsReportedEvent.class);
        verify(eventPublisher).publishEvent(reported.capture());
        assertEquals(List.of("ext-luces"), reported.getValue().itemIds());
    }

    @Test
    void reportaUnBloqueante_avisaYDejaElVehiculoNoDisponibleConElViajeAbierto() throws Exception {
        Trip trip = openTrip(driver.id());

        service.report(vehicle.getId().toString(), request("ext-luces", "blocking", "http://x/foto.jpg"), driver);

        assertTrue(savedInspection().isHasBlockingDefect());
        ArgumentCaptor<BlockingDefectReportedEvent> event = ArgumentCaptor.forClass(BlockingDefectReportedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertEquals(InspectionType.ON_ROAD, event.getValue().inspectionType());
        assertEquals(List.of("Sin luz de freno"), event.getValue().defectTitles());
        assertEquals("chofer1", event.getValue().driverName());
        verify(availabilityService).sync(vehicle, null);
        assertEquals(TripStatus.OPEN, trip.getStatus());
    }

    @Test
    void unBloqueanteSinFoto_da422() throws Exception {
        openTrip(driver.id());

        InspectionValidationException ex = assertThrows(InspectionValidationException.class,
                () -> service.report(vehicle.getId().toString(), request("ext-luces", "blocking", null), driver));

        assertEquals("ext-luces", ex.getDetails().get(0).itemId());
        verify(inspectionRepository, never()).save(any());
    }

    @Test
    void sinDetalleDelDefecto_da422() throws Exception {
        openTrip(driver.id());

        assertThrows(InspectionValidationException.class, () -> service.report(vehicle.getId().toString(),
                new RoadDefectRequest("ext-luces", null), driver));
        verify(inspectionRepository, never()).save(any());
    }

    @Test
    void unTituloDeMasDe30Caracteres_da422() throws Exception {
        openTrip(driver.id());

        assertThrows(InspectionValidationException.class, () -> service.report(vehicle.getId().toString(),
                new RoadDefectRequest("ext-luces", new DefectDetailDto("non-blocking", "x".repeat(31), null)), driver));
    }

    @Test
    void otroProblema_seGuardaPeroNoCuentaParaRecurrentes() throws Exception {
        openTrip(driver.id());

        service.report(vehicle.getId().toString(), request("road-otro", "non-blocking", null), driver);

        assertEquals("Otro problema", savedInspection().getAnswers().get(0).getItemLabel());
        verify(eventPublisher, never()).publishEvent(any(DefectsReportedEvent.class));
    }

    @Test
    void unControlPropioDelVehiculo_seAcepta() throws Exception {
        openTrip(driver.id());

        service.report(vehicle.getId().toString(), request("custom-grua", "non-blocking", null), driver);

        assertEquals("Grúa", savedInspection().getAnswers().get(0).getItemLabel());
    }

    @Test
    void elControlDeKmOUnoDelPostViaje_noSePuedenElegir() throws Exception {
        openTrip(driver.id());

        for (String itemId : List.of(ChecklistCatalog.ODOMETER_ITEM_ID_PRE_TRIP, "post-danos", "inventado")) {
            InspectionValidationException ex = assertThrows(InspectionValidationException.class,
                    () -> service.report(vehicle.getId().toString(), request(itemId, "non-blocking", null), driver));
            assertEquals("itemId", ex.getDetails().get(0).itemId());
        }
        verify(inspectionRepository, never()).save(any());
    }

    @Test
    void sinControlOSinCuerpo_da422() throws Exception {
        openTrip(driver.id());

        assertThrows(InspectionValidationException.class,
                () -> service.report(vehicle.getId().toString(), request(" ", "non-blocking", null), driver));
        assertThrows(InspectionValidationException.class,
                () -> service.report(vehicle.getId().toString(), null, driver));
    }

    @Test
    void sinViajeAbierto_da409() {
        when(tripRepository.findFirstByVehicle_IdAndStatus(vehicle.getId(), TripStatus.OPEN)).thenReturn(Optional.empty());

        VehicleStateConflictException ex = assertThrows(VehicleStateConflictException.class,
                () -> service.report(vehicle.getId().toString(), request("ext-luces", "non-blocking", null), driver));

        assertEquals("NO_OPEN_TRIP", ex.getErrorCode());
        verify(inspectionRepository, never()).save(any());
    }

    @Test
    void conElViajeDeOtroChofer_da409() throws Exception {
        openTrip(UUID.randomUUID());

        VehicleStateConflictException ex = assertThrows(VehicleStateConflictException.class,
                () -> service.report(vehicle.getId().toString(), request("ext-luces", "non-blocking", null), driver));

        assertEquals("NOT_YOUR_TRIP", ex.getErrorCode());
    }

    @Test
    void conUnViajeSinChoferRegistrado_da409() throws Exception {
        openTrip(null);

        VehicleStateConflictException ex = assertThrows(VehicleStateConflictException.class,
                () -> service.report(vehicle.getId().toString(), request("ext-luces", "non-blocking", null), driver));

        assertEquals("NOT_YOUR_TRIP", ex.getErrorCode());
    }

    @Test
    void unVehiculoInexistenteOUnIdInvalido_da404() {
        when(vehicleRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(VehicleNotFoundException.class,
                () -> service.report(UUID.randomUUID().toString(), request("ext-luces", "non-blocking", null), driver));
        assertThrows(VehicleNotFoundException.class,
                () -> service.report("no-es-un-uuid", request("ext-luces", "non-blocking", null), driver));
    }
}
