package org.fleetguard.service;

import org.fleetguard.auth.AuthenticatedUser;
import org.fleetguard.dto.BlockReasonDto;
import org.fleetguard.dto.ChecklistAnswerDto;
import org.fleetguard.dto.DefectDetailDto;
import org.fleetguard.dto.InspectionResultDto;
import org.fleetguard.dto.InspectionSubmissionDto;
import org.fleetguard.entity.Inspection;
import org.fleetguard.entity.InspectionType;
import org.fleetguard.entity.Role;
import org.fleetguard.entity.Trip;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.VehicleType;
import org.fleetguard.entity.checklist.ChecklistCatalog;
import org.fleetguard.event.BlockingDefectReportedEvent;
import org.fleetguard.event.TripStartedEvent;
import org.fleetguard.exception.InspectionValidationException;
import org.fleetguard.exception.VehicleStateConflictException;
import org.fleetguard.mapper.InspectionMapper;
import org.fleetguard.mapper.TripMapper;
import org.fleetguard.repository.InspectionRepository;
import org.fleetguard.repository.TripRepository;
import org.fleetguard.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// CAM-159: el remolque que sale con un pre-viaje se revisa en ese mismo envío, con su propio checklist.
class InspectionServiceTrailerTest {

    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final TripRepository tripRepository = mock(TripRepository.class);
    private final InspectionRepository inspectionRepository = mock(InspectionRepository.class);
    private final VehicleAvailabilityService availabilityService = mock(VehicleAvailabilityService.class);
    private final VehicleChecklistService checklistService = mock(VehicleChecklistService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final TireService tireService = mock(TireService.class);
    private final CouplingService couplingService = mock(CouplingService.class);
    private final InspectionService service = new InspectionService(vehicleRepository, tripRepository,
            inspectionRepository, new InspectionMapper(), new TripMapper(), checklistService, new OdometerReadings(5000),
            availabilityService, mock(TripKmAlertService.class), eventPublisher, tireService, couplingService);

    private final AuthenticatedUser driver = new AuthenticatedUser(UUID.randomUUID(), "chofer1", Role.CHOFER);
    private final List<Inspection> saved = new ArrayList<>();
    private Vehicle tractor;
    private Vehicle semi;

    private static void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    private Vehicle vehicle(String plate, VehicleType type) throws Exception {
        Vehicle vehicle = new Vehicle(plate, "Scania", "R450");
        vehicle.setVehicleType(type);
        setId(vehicle, UUID.randomUUID());
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        return vehicle;
    }

    @BeforeEach
    void setUp() throws Exception {
        tractor = vehicle("AB123CD", VehicleType.TRACTOR_4X2);
        semi = vehicle("ZZ001AA", VehicleType.SEMIRREMOLQUE_3_EJES);
        when(checklistService.resolve(tractor.getId(), InspectionType.PRE_TRIP)).thenReturn(ChecklistCatalog.preTripItems());
        when(checklistService.resolve(semi.getId(), InspectionType.PRE_TRIP))
                .thenReturn(ChecklistCatalog.trailerItems(VehicleType.SEMIRREMOLQUE_3_EJES));
        when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> {
            Trip trip = invocation.getArgument(0);
            if (trip.getId() == null) {
                setId(trip, UUID.randomUUID());
            }
            return trip;
        });
        when(inspectionRepository.save(any(Inspection.class))).thenAnswer(invocation -> {
            Inspection inspection = invocation.getArgument(0);
            setId(inspection, UUID.randomUUID());
            saved.add(inspection);
            return inspection;
        });
        when(couplingService.currentTrailer(tractor)).thenReturn(Optional.of(semi));
    }

    private static List<ChecklistAnswerDto> tractorOk() {
        return List.of(
                new ChecklistAnswerDto("ext-luces", "ok", null, null),
                new ChecklistAnswerDto("ext-neumaticos", "ok", null, null),
                new ChecklistAnswerDto("ext-carroceria", "ok", null, null),
                new ChecklistAnswerDto("ext-fugas", "ok", null, null),
                new ChecklistAnswerDto("int-km", null, 12345.0, null),
                new ChecklistAnswerDto("int-documentacion", "ok", null, null),
                new ChecklistAnswerDto("int-testigos", "ok", null, null));
    }

    private static List<ChecklistAnswerDto> trailerAnswers(ChecklistAnswerDto... overrides) {
        List<ChecklistAnswerDto> answers = new ArrayList<>();
        for (var item : ChecklistCatalog.trailerItems(VehicleType.SEMIRREMOLQUE_3_EJES)) {
            answers.add(List.of(overrides).stream().filter(o -> o.itemId().equals(item.id())).findFirst()
                    .orElse(new ChecklistAnswerDto(item.id(), "ok", null, null)));
        }
        return answers;
    }

    private InspectionResultDto submit(String trailerId, List<ChecklistAnswerDto> trailer) {
        return service.submit(tractor.getId().toString(), new InspectionSubmissionDto("pre-trip", tractorOk(), null,
                null, null, null, trailerId, trailer, null), driver);
    }

    @Test
    void elRemolqueEnganchadoQuedaConSuInspeccionEnElMismoViaje() {
        InspectionResultDto result = submit(null, trailerAnswers());

        assertEquals(2, saved.size());
        Inspection truck = saved.get(0);
        Inspection trailer = saved.get(1);
        assertEquals(semi.getId(), trailer.getVehicleId());
        assertEquals(truck.getId(), trailer.getTowingInspectionId());
        assertEquals(truck.getTrip(), trailer.getTrip());
        assertNull(truck.getTowingInspectionId());
        assertEquals(semi.getId().toString(), result.trailerInspection().vehicleId());
        assertEquals("open", result.trip().status());
        verify(eventPublisher).publishEvent(any(TripStartedEvent.class));
    }

    @Test
    void unBloqueanteEnElRemolqueNoDejaSalirAlViaje() {
        InspectionResultDto result = submit(null, trailerAnswers(new ChecklistAnswerDto("rem-frenos", "defect", null,
                new DefectDetailDto("blocking", "No frena", "http://localhost:8080/api/photos/1"))));

        assertEquals("closed", result.trip().status());
        assertFalse(result.inspection().hasBlockingDefect());
        assertTrue(result.trailerInspection().hasBlockingDefect());
        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(events.capture());
        BlockingDefectReportedEvent blocking = events.getAllValues().stream()
                .filter(BlockingDefectReportedEvent.class::isInstance).map(BlockingDefectReportedEvent.class::cast)
                .findFirst().orElseThrow();
        assertEquals("ZZ001AA", blocking.plate());
        assertTrue(events.getAllValues().stream().noneMatch(TripStartedEvent.class::isInstance));
        verify(availabilityService).sync(semi, null);
    }

    @Test
    void conRemolqueHayQueMandarSuChecklist() {
        InspectionValidationException ex = assertThrows(InspectionValidationException.class, () -> submit(null, null));

        assertEquals("trailerAnswers", ex.getDetails().get(0).itemId());
        assertTrue(saved.isEmpty());
    }

    @Test
    void unRemolqueQueNoPuedeSalirFrenaElPreViaje() {
        when(availabilityService.reasons(semi.getId()))
                .thenReturn(List.of(new BlockReasonDto("expired_document", "VTV vencida")));

        VehicleStateConflictException ex = assertThrows(VehicleStateConflictException.class,
                () -> submit(null, trailerAnswers()));

        assertEquals("TRAILER_BLOCKED", ex.getErrorCode());
        assertTrue(saved.isEmpty());
    }

    @Test
    void sinRemolqueNoSeRevisaNinguno() {
        when(couplingService.currentTrailer(tractor)).thenReturn(Optional.empty());

        InspectionResultDto result = submit(null, null);

        assertEquals(1, saved.size());
        assertNull(result.trailerInspection());
    }

    @Test
    void elegirSinRemolqueNoPideElChecklistDelQueTenia() {
        InspectionResultDto result = submit("none", null);

        assertEquals(1, saved.size());
        assertNull(result.trailerInspection());
        verify(couplingService, never()).currentTrailer(any());
        verify(couplingService).applyInspectionTrailer(eq(tractor), eq("none"), eq(Optional.empty()), any());
    }
}
