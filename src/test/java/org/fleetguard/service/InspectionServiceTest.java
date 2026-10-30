package org.fleetguard.service;

import org.fleetguard.auth.AuthenticatedUser;
import org.fleetguard.dto.ChecklistAnswerDto;
import org.fleetguard.dto.DefectDetailDto;
import org.fleetguard.dto.InspectionResultDto;
import org.fleetguard.dto.InspectionSubmissionDto;
import org.fleetguard.entity.CheckOutcome;
import org.fleetguard.entity.Defect;
import org.fleetguard.entity.DefectSeverity;
import org.fleetguard.entity.Inspection;
import org.fleetguard.entity.InspectionAnswer;
import org.fleetguard.entity.InspectionType;
import org.fleetguard.entity.MaintenanceStatus;
import org.fleetguard.entity.Role;
import org.fleetguard.entity.Trip;
import org.fleetguard.entity.TripStatus;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.checklist.ChecklistCatalog;
import org.fleetguard.exception.InspectionValidationException;
import org.fleetguard.exception.VehicleNotFoundException;
import org.fleetguard.exception.VehicleStateConflictException;
import org.fleetguard.mapper.InspectionMapper;
import org.fleetguard.mapper.TripMapper;
import org.fleetguard.repository.DefectRepository;
import org.fleetguard.repository.InspectionRepository;
import org.fleetguard.repository.TripRepository;
import org.fleetguard.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.fleetguard.event.BlockingDefectReportedEvent;
import org.fleetguard.event.DefectsReportedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Caso de uso central del DVIR (CAM-11): un pre-trip abre el viaje, un post-trip lo cierra, y
 * cada respuesta "defect" genera un defecto. Las reglas de validación de cada respuesta ya las
 * cubre InspectionValidatorTest; acá se prueba lo que hace el servicio con el resultado: quién
 * queda en el viaje (CAM-85, CAM-87) y cuándo un vehículo no puede salir (CAM-86, CAM-89).
 */
class InspectionServiceTest {

    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final TripRepository tripRepository = mock(TripRepository.class);
    private final InspectionRepository inspectionRepository = mock(InspectionRepository.class);
    private final DefectRepository defectRepository = mock(DefectRepository.class);
    private final VehicleChecklistService checklistService = mock(VehicleChecklistService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final InspectionService service = new InspectionService(vehicleRepository, tripRepository,
            inspectionRepository, defectRepository, new InspectionMapper(), new TripMapper(), checklistService, new OdometerReadings(5000), eventPublisher);

    private final AuthenticatedUser driver = new AuthenticatedUser(UUID.randomUUID(), "chofer1", Role.CHOFER);
    private final AuthenticatedUser otherDriver = new AuthenticatedUser(UUID.randomUUID(), "chofer2", Role.CHOFER);
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
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(checklistService.resolve(vehicle.getId(), InspectionType.PRE_TRIP)).thenReturn(ChecklistCatalog.preTripItems());
        when(checklistService.resolve(vehicle.getId(), InspectionType.POST_TRIP)).thenReturn(ChecklistCatalog.postTripItems());
        // Los ids son @GeneratedValue: el mock de save los asigna como lo haría JPA.
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
            return inspection;
        });
    }

    private void noOpenTrip() {
        when(tripRepository.findFirstByVehicle_IdAndStatus(vehicle.getId(), TripStatus.OPEN)).thenReturn(Optional.empty());
    }

    private Trip openTrip() throws Exception {
        Trip trip = new Trip(vehicle, Instant.now().minusSeconds(3600), driver.id(), driver.username());
        setId(trip, UUID.randomUUID());
        when(tripRepository.findFirstByVehicle_IdAndStatus(vehicle.getId(), TripStatus.OPEN)).thenReturn(Optional.of(trip));
        return trip;
    }

    private static List<ChecklistAnswerDto> preTripAllOk() {
        return new ArrayList<>(List.of(
                new ChecklistAnswerDto("ext-luces", "ok", null, null),
                new ChecklistAnswerDto("ext-neumaticos", "ok", null, null),
                new ChecklistAnswerDto("ext-carroceria", "ok", null, null),
                new ChecklistAnswerDto("ext-fugas", "ok", null, null),
                new ChecklistAnswerDto("int-km", null, 12345.0, null),
                new ChecklistAnswerDto("int-documentacion", "ok", null, null),
                new ChecklistAnswerDto("int-testigos", "ok", null, null)));
    }

    private static List<ChecklistAnswerDto> postTripAllOk() {
        return List.of(
                new ChecklistAnswerDto("post-danos", "ok", null, null),
                new ChecklistAnswerDto("post-luces", "ok", null, null),
                new ChecklistAnswerDto("post-fugas", "ok", null, null),
                new ChecklistAnswerDto("post-km", null, 12500.0, null));
    }

    private static List<ChecklistAnswerDto> withAnswer(List<ChecklistAnswerDto> answers, ChecklistAnswerDto replacement) {
        List<ChecklistAnswerDto> result = new ArrayList<>(answers);
        result.removeIf(a -> a.itemId().equals(replacement.itemId()));
        result.add(replacement);
        return result;
    }

    private Inspection savedInspection() {
        ArgumentCaptor<Inspection> captor = ArgumentCaptor.forClass(Inspection.class);
        verify(inspectionRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void unPreTripSinViajeAbiertoAbreElViajeYGuardaLaInspeccion() {
        noOpenTrip();

        InspectionResultDto result = service.submit(vehicle.getId().toString(),
                new InspectionSubmissionDto("pre-trip", preTripAllOk(), "Todo en orden"), driver);

        assertEquals("open", result.trip().status());
        assertNull(result.trip().endedAt());
        Inspection saved = savedInspection();
        assertEquals(InspectionType.PRE_TRIP, saved.getType());
        // CAM-85: el chofer es el del token, con su id de usuario.
        assertEquals(driver.id().toString(), saved.getDriverId());
        assertEquals("chofer1", saved.getDriverName());
        assertEquals(12345.0, saved.getOdometerKm());
        assertEquals(7, saved.getAnswers().size());
        assertFalse(saved.isHasBlockingDefect());
    }

    @Test
    void unPreTripConElViajeYaAbiertoSeRechazaSinGuardarNada() throws Exception {
        openTrip();

        VehicleStateConflictException ex = assertThrows(VehicleStateConflictException.class,
                () -> service.submit(vehicle.getId().toString(),
                        new InspectionSubmissionDto("pre-trip", preTripAllOk(), null), driver));

        assertEquals("VEHICLE_ON_TRIP", ex.getErrorCode());
        verify(tripRepository, never()).save(any());
        verify(inspectionRepository, never()).save(any());
    }

    @Test
    void unPostTripCierraElViajeAbierto() throws Exception {
        Trip trip = openTrip();

        InspectionResultDto result = service.submit(vehicle.getId().toString(),
                new InspectionSubmissionDto("post-trip", postTripAllOk(), null), driver);

        assertEquals(TripStatus.CLOSED, trip.getStatus());
        assertNotNull(trip.getEndedAt());
        assertEquals("closed", result.trip().status());
        assertEquals(12500.0, savedInspection().getOdometerKm());
    }

    @Test
    void elViajeRegistraQuienLoAbre() {
        noOpenTrip();

        InspectionResultDto result = service.submit(vehicle.getId().toString(),
                new InspectionSubmissionDto("pre-trip", preTripAllOk(), null), driver);

        assertEquals(driver.id().toString(), result.trip().openedBy().id());
        assertEquals("chofer1", result.trip().openedBy().name());
        assertNull(result.trip().closedBy());
    }

    @Test
    void siElPostTripLoHaceOtroChoferQuedaAbiertoPorUnoYCerradoPorOtro() throws Exception {
        Trip trip = openTrip();

        InspectionResultDto result = service.submit(vehicle.getId().toString(),
                new InspectionSubmissionDto("post-trip", postTripAllOk(), null), otherDriver);

        // CAM-87: hoy cualquier chofer puede cerrar el viaje (CAM-33 lo va a impedir), pero queda registrado.
        assertEquals(driver.id(), trip.getOpenedById());
        assertEquals(otherDriver.id(), trip.getClosedById());
        assertEquals("chofer1", result.trip().openedBy().name());
        assertEquals("chofer2", result.trip().closedBy().name());
        assertEquals(otherDriver.id().toString(), savedInspection().getDriverId());
    }

    @Test
    void unViajeAnteriorSinChoferSeCierraSinErrores() throws Exception {
        Trip legacy = new Trip(vehicle, Instant.now().minusSeconds(3600));
        setId(legacy, UUID.randomUUID());
        when(tripRepository.findFirstByVehicle_IdAndStatus(vehicle.getId(), TripStatus.OPEN)).thenReturn(Optional.of(legacy));

        InspectionResultDto result = service.submit(vehicle.getId().toString(),
                new InspectionSubmissionDto("post-trip", postTripAllOk(), null), driver);

        assertNull(result.trip().openedBy());
        assertEquals("chofer1", result.trip().closedBy().name());
    }

    @Test
    void unPreTripSobreUnVehiculoBloqueadoSeRechazaSinAbrirViaje() {
        noOpenTrip();
        when(defectRepository.existsOpenBlockingByVehicleId(vehicle.getId())).thenReturn(true);

        VehicleStateConflictException ex = assertThrows(VehicleStateConflictException.class,
                () -> service.submit(vehicle.getId().toString(),
                        new InspectionSubmissionDto("pre-trip", preTripAllOk(), null), driver));

        assertEquals("VEHICLE_BLOCKED", ex.getErrorCode());
        verify(tripRepository, never()).save(any());
        verify(inspectionRepository, never()).save(any());
    }

    @Test
    void unPreTripSobreUnVehiculoDadoDeBajaSeRechaza() {
        noOpenTrip();
        vehicle.setActive(false);

        VehicleStateConflictException ex = assertThrows(VehicleStateConflictException.class,
                () -> service.submit(vehicle.getId().toString(),
                        new InspectionSubmissionDto("pre-trip", preTripAllOk(), null), driver));

        assertEquals("VEHICLE_INACTIVE", ex.getErrorCode());
        verify(tripRepository, never()).save(any());
    }

    @Test
    void unPostTripSeAceptaAunqueElVehiculoEsteBloqueado() throws Exception {
        Trip trip = openTrip();
        when(defectRepository.existsOpenBlockingByVehicleId(vehicle.getId())).thenReturn(true);

        service.submit(vehicle.getId().toString(), new InspectionSubmissionDto("post-trip", postTripAllOk(), null), driver);

        // CAM-89: un viaje abierto se tiene que poder cerrar siempre.
        assertEquals(TripStatus.CLOSED, trip.getStatus());
    }

    @Test
    void unPreTripConDefectoBloqueanteNoDejaElVehiculoEnViaje() {
        noOpenTrip();
        List<ChecklistAnswerDto> answers = withAnswer(preTripAllOk(), new ChecklistAnswerDto("ext-neumaticos", "defect", null,
                new DefectDetailDto("blocking", "Neumático cortado", "http://localhost/foto.jpg")));

        InspectionResultDto result = service.submit(vehicle.getId().toString(),
                new InspectionSubmissionDto("pre-trip", answers, null), driver);

        // CAM-86: la inspección y el defecto se guardan, pero el viaje nace cerrado (inicio = fin).
        assertEquals("closed", result.trip().status());
        assertEquals(result.trip().startedAt(), result.trip().endedAt());
        assertEquals("chofer1", result.trip().closedBy().name());
        assertTrue(savedInspection().isHasBlockingDefect());
    }

    @Test
    void unDefectoBloqueantePublicaElEventoDelAvisoAlAdmin() {
        noOpenTrip();
        List<ChecklistAnswerDto> answers = withAnswer(preTripAllOk(), new ChecklistAnswerDto("ext-neumaticos", "defect", null,
                new DefectDetailDto("blocking", "Neumático cortado", "http://localhost/foto.jpg")));

        service.submit(vehicle.getId().toString(), new InspectionSubmissionDto("pre-trip", answers, null), driver);

        // CAM-97: un evento por inspección, con todo lo que dice el aviso.
        ArgumentCaptor<BlockingDefectReportedEvent> event = ArgumentCaptor.forClass(BlockingDefectReportedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertEquals(vehicle.getId(), event.getValue().vehicleId());
        assertEquals(InspectionType.PRE_TRIP, event.getValue().inspectionType());
        assertEquals(List.of("Neumático cortado"), event.getValue().defectTitles());
        assertEquals("chofer1", event.getValue().driverName());
    }

    @Test
    void unDefectoNoBloqueanteNoPublicaElEvento() {
        noOpenTrip();
        List<ChecklistAnswerDto> answers = withAnswer(preTripAllOk(), new ChecklistAnswerDto("ext-neumaticos", "defect", null,
                new DefectDetailDto("non-blocking", "Foco trasero tenue", null)));

        service.submit(vehicle.getId().toString(), new InspectionSubmissionDto("pre-trip", answers, null), driver);

        verify(eventPublisher, never()).publishEvent(any(BlockingDefectReportedEvent.class));
    }

    @Test
    void cualquierDefectoPublicaLosControlesQueFallaronParaVerSiSonRecurrentes() {
        noOpenTrip();
        List<ChecklistAnswerDto> answers = withAnswer(preTripAllOk(), new ChecklistAnswerDto("ext-neumaticos", "defect", null,
                new DefectDetailDto("blocking", "Neumático cortado", "http://localhost/foto.jpg")));
        answers = withAnswer(answers, new ChecklistAnswerDto("ext-luces", "defect", null,
                new DefectDetailDto("non-blocking", "Foco trasero tenue", null)));

        service.submit(vehicle.getId().toString(), new InspectionSubmissionDto("pre-trip", answers, null), driver);

        // CAM-135: bloqueantes y no bloqueantes, en un solo evento por inspección.
        ArgumentCaptor<DefectsReportedEvent> event = ArgumentCaptor.forClass(DefectsReportedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertEquals(vehicle.getId(), event.getValue().vehicleId());
        assertEquals(vehicle.getPlate(), event.getValue().plate());
        assertEquals(Set.of("ext-neumaticos", "ext-luces"), Set.copyOf(event.getValue().itemIds()));
    }

    @Test
    void sinDefectosNoPublicaElEventoDeRecurrentes() {
        noOpenTrip();

        service.submit(vehicle.getId().toString(), new InspectionSubmissionDto("pre-trip", preTripAllOk(), null), driver);

        verify(eventPublisher, never()).publishEvent(any(DefectsReportedEvent.class));
    }

    @Test
    void unPostTripConDefectoBloqueanteCierraElViajeIgualQueSiempre() throws Exception {
        Trip trip = openTrip();
        List<ChecklistAnswerDto> answers = withAnswer(postTripAllOk(), new ChecklistAnswerDto("post-fugas", "defect", null,
                new DefectDetailDto("blocking", "Pérdida de aceite", "http://localhost/foto.jpg")));

        service.submit(vehicle.getId().toString(), new InspectionSubmissionDto("post-trip", answers, null), driver);

        assertEquals(TripStatus.CLOSED, trip.getStatus());
        assertTrue(trip.getEndedAt().isAfter(trip.getStartedAt()));
    }

    @Test
    void unPostTripSinViajeAbiertoSeRechaza() {
        noOpenTrip();

        VehicleStateConflictException ex = assertThrows(VehicleStateConflictException.class,
                () -> service.submit(vehicle.getId().toString(),
                        new InspectionSubmissionDto("post-trip", postTripAllOk(), null), driver));

        assertEquals("NO_OPEN_TRIP", ex.getErrorCode());
        verify(inspectionRepository, never()).save(any());
    }

    @Test
    void unaRespuestaConDefectoCreaElDefectoConSuDescripcionLarga() {
        noOpenTrip();
        List<ChecklistAnswerDto> answers = withAnswer(preTripAllOk(), new ChecklistAnswerDto("ext-luces", "defect", null,
                new DefectDetailDto("non-blocking", "Foco trasero tenue", null, "  Parpadea al frenar.  ")));

        service.submit(vehicle.getId().toString(), new InspectionSubmissionDto("pre-trip", answers, null), driver);

        InspectionAnswer luces = savedInspection().getAnswers().stream()
                .filter(a -> a.getItemId().equals("ext-luces")).findFirst().orElseThrow();
        assertEquals(CheckOutcome.DEFECT, luces.getOutcome());
        assertEquals("Luces", luces.getItemLabel());
        Defect defect = luces.getDefect();
        assertEquals(DefectSeverity.NON_BLOCKING, defect.getSeverity());
        assertEquals("Foco trasero tenue", defect.getDescription());
        // CAM-32: la descripción larga se guarda sin espacios en los extremos.
        assertEquals("Parpadea al frenar.", defect.getDetails());
    }

    @Test
    void unaDescripcionLargaEnBlancoSeGuardaComoNull() {
        noOpenTrip();
        List<ChecklistAnswerDto> answers = withAnswer(preTripAllOk(), new ChecklistAnswerDto("ext-luces", "defect", null,
                new DefectDetailDto("non-blocking", "Foco trasero tenue", null, "   ")));

        service.submit(vehicle.getId().toString(), new InspectionSubmissionDto("pre-trip", answers, null), driver);

        Defect defect = savedInspection().getAnswers().stream()
                .filter(a -> a.getDefect() != null).findFirst().orElseThrow().getDefect();
        assertNull(defect.getDetails());
    }

    @Test
    void unDefectoBloqueanteMarcaLaInspeccion() {
        noOpenTrip();
        List<ChecklistAnswerDto> answers = withAnswer(preTripAllOk(), new ChecklistAnswerDto("ext-neumaticos", "defect", null,
                new DefectDetailDto("blocking", "Neumático cortado", "http://localhost/foto.jpg")));

        service.submit(vehicle.getId().toString(), new InspectionSubmissionDto("pre-trip", answers, null), driver);

        // Es lo que después deja al vehículo "No disponible" en la flota (CAM-49).
        assertTrue(savedInspection().isHasBlockingDefect());
    }

    @Test
    void unaInspeccionInvalidaNoAbreElViaje() {
        noOpenTrip();
        List<ChecklistAnswerDto> sinKm = new ArrayList<>(preTripAllOk());
        sinKm.removeIf(a -> a.itemId().equals("int-km"));

        assertThrows(InspectionValidationException.class, () -> service.submit(vehicle.getId().toString(),
                new InspectionSubmissionDto("pre-trip", sinKm, null), driver));

        // La validación corre antes de tocar el viaje: un envío rechazado no deja un viaje abierto colgado.
        verify(tripRepository, never()).save(any());
    }

    @Test
    void unTipoDeInspeccionInvalidoDevuelve422() {
        InspectionValidationException ex = assertThrows(InspectionValidationException.class,
                () -> service.submit(vehicle.getId().toString(),
                        new InspectionSubmissionDto("mid-trip", preTripAllOk(), null), driver));

        assertEquals("type", ex.getDetails().get(0).itemId());
    }

    @Test
    void sinRespuestasSeValidaComoListaVaciaYNoExplota() {
        noOpenTrip();

        // answers null no es un NullPointerException: se rechaza por faltar los ítems obligatorios.
        assertThrows(InspectionValidationException.class, () -> service.submit(vehicle.getId().toString(),
                new InspectionSubmissionDto("pre-trip", null, null), driver));
    }

    @Test
    void unVehiculoInexistenteOMalformadoDevuelve404() {
        UUID missing = UUID.randomUUID();
        when(vehicleRepository.findById(missing)).thenReturn(Optional.empty());

        assertThrows(VehicleNotFoundException.class, () -> service.submit(missing.toString(),
                new InspectionSubmissionDto("pre-trip", preTripAllOk(), null), driver));
        assertThrows(VehicleNotFoundException.class, () -> service.submit("no-es-un-uuid",
                new InspectionSubmissionDto("pre-trip", preTripAllOk(), null), driver));
    }

    // --- CAM-90: el km de la inspección avanza el del vehículo ---

    @Test
    void unPostTripConMasKmActualizaElVehiculo() throws Exception {
        vehicle.setOdometerKm(120000);
        openTrip();

        service.submit(vehicle.getId().toString(), new InspectionSubmissionDto("post-trip",
                withAnswer(postTripAllOk(), new ChecklistAnswerDto("post-km", null, 120350.0, null)), null), driver);

        assertEquals(120350, vehicle.getOdometerKm());
        // El estado de cada plan se calcula contra este km: uno que vencía a los 120.300 pasa a vencido.
        assertEquals(MaintenanceStatus.VENCIDO,
                MaintenanceStatusCalculator.computeStatus(120300L, null, vehicle.getOdometerKm(), LocalDate.now()));
    }

    @Test
    void unaInspeccionConMenosKmSeGuardaYElVehiculoNoCambia() {
        vehicle.setOdometerKm(120000);
        noOpenTrip();

        service.submit(vehicle.getId().toString(), new InspectionSubmissionDto("pre-trip",
                withAnswer(preTripAllOk(), new ChecklistAnswerDto("int-km", null, 119000.0, null)), null), driver);

        assertEquals(119000.0, savedInspection().getOdometerKm());
        assertEquals(120000, vehicle.getOdometerKm());
    }

    @Test
    void elKmConDecimalesActualizaConLaParteEntera() {
        vehicle.setOdometerKm(120000);
        noOpenTrip();

        service.submit(vehicle.getId().toString(), new InspectionSubmissionDto("pre-trip",
                withAnswer(preTripAllOk(), new ChecklistAnswerDto("int-km", null, 120350.9, null)), null), driver);

        assertEquals(120350.9, savedInspection().getOdometerKm());
        assertEquals(120350, vehicle.getOdometerKm());
    }

    @Test
    void unaLecturaPorEncimaDelTopeNoActualizaElVehiculo() {
        vehicle.setOdometerKm(120000);
        noOpenTrip();

        service.submit(vehicle.getId().toString(), new InspectionSubmissionDto("pre-trip",
                withAnswer(preTripAllOk(), new ChecklistAnswerDto("int-km", null, 1203500.0, null)), null), driver);

        assertEquals(1203500.0, savedInspection().getOdometerKm());
        assertEquals(120000, vehicle.getOdometerKm());
    }
}
