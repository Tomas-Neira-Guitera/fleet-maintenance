package org.fleetguard.service;

import org.fleetguard.dto.RecurringDefectsDto;
import org.fleetguard.entity.CheckOutcome;
import org.fleetguard.entity.Defect;
import org.fleetguard.entity.DefectSeverity;
import org.fleetguard.entity.Inspection;
import org.fleetguard.entity.InspectionAnswer;
import org.fleetguard.entity.InspectionType;
import org.fleetguard.entity.NotificationType;
import org.fleetguard.entity.RecurringDefectAlert;
import org.fleetguard.entity.Trip;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.repository.DefectRepository;
import org.fleetguard.repository.RecurringDefectAlertRepository;
import org.fleetguard.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CAM-135 -- un control es recurrente cuando falla 3 veces en 30 días en el mismo vehículo, y se le
 * avisa al admin una sola vez por ventana.
 */
class RecurringDefectServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-29T15:00:00Z");
    private static final Instant WINDOW_START = NOW.minus(Duration.ofDays(30));

    private final DefectRepository defectRepository = mock(DefectRepository.class);
    private final RecurringDefectAlertRepository alertRepository = mock(RecurringDefectAlertRepository.class);
    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final RecurringDefectService service = new RecurringDefectService(defectRepository, alertRepository,
            vehicleRepository, notificationService, new NotificationDispatcher(transactionManager),
            Clock.fixed(NOW, ZoneOffset.UTC), 3, 30);

    private Vehicle vehicle;
    private Vehicle otherVehicle;

    private static void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    /** Un defecto del control {@code itemId}, reportado {@code daysAgo} días antes de NOW. */
    private static Defect defect(Vehicle vehicle, String itemId, String itemLabel, DefectSeverity severity,
                                 long daysAgo) throws Exception {
        Instant at = NOW.minus(Duration.ofDays(daysAgo));
        Trip trip = new Trip(vehicle, at, UUID.randomUUID(), "chofer1");
        Inspection inspection = new Inspection(trip, vehicle.getId(), UUID.randomUUID().toString(), "chofer1",
                InspectionType.PRE_TRIP, at, null, null, severity == DefectSeverity.BLOCKING);
        InspectionAnswer answer = new InspectionAnswer(itemId, itemLabel, CheckOutcome.DEFECT, null);
        inspection.addAnswer(answer);
        Defect defect = new Defect(severity, "Falla de " + itemLabel, null, at);
        setId(defect, UUID.randomUUID());
        answer.attachDefect(defect);
        return defect;
    }

    private static Defect brakes(Vehicle vehicle, long daysAgo) throws Exception {
        return defect(vehicle, "frenos", "Frenos", DefectSeverity.BLOCKING, daysAgo);
    }

    @BeforeEach
    void setUp() throws Exception {
        vehicle = new Vehicle("AB123CD", "Ford", "Cargo");
        setId(vehicle, UUID.randomUUID());
        otherVehicle = new Vehicle("AC456EF", "Fiat", "Ducato");
        setId(otherVehicle, UUID.randomUUID());
        when(vehicleRepository.findAllById(any())).thenReturn(List.of(vehicle, otherVehicle));
    }

    // --- aviso ---

    @Test
    void laTerceraFallaDelMismoControlEn30DiasLeAvisaAlAdminYQuedaMarcada() throws Exception {
        when(defectRepository.findReportedSinceByVehicleIdWithInspection(vehicle.getId(), WINDOW_START))
                .thenReturn(List.of(brakes(vehicle, 20), brakes(vehicle, 10), brakes(vehicle, 0)));

        assertEquals(1, service.alertIfRecurring(vehicle.getId(), "AB123CD", List.of("frenos")));

        verify(notificationService).notifyConfiguredRecipients(NotificationType.RECURRING_DEFECT,
                "Defecto recurrente en AB123CD", "Frenos de AB123CD: 3 defectos en 30 días.",
                "/vehiculos/" + vehicle.getId());
        ArgumentCaptor<RecurringDefectAlert> saved = ArgumentCaptor.forClass(RecurringDefectAlert.class);
        verify(alertRepository).save(saved.capture());
        assertEquals(vehicle.getId(), saved.getValue().getVehicleId());
        assertEquals("frenos", saved.getValue().getItemId());
        assertEquals(NOW, saved.getValue().getSentAt());
    }

    @Test
    void cuentanTambienLosNoBloqueantes() throws Exception {
        when(defectRepository.findReportedSinceByVehicleIdWithInspection(vehicle.getId(), WINDOW_START))
                .thenReturn(List.of(defect(vehicle, "luces", "Luces", DefectSeverity.NON_BLOCKING, 15),
                        defect(vehicle, "luces", "Luces", DefectSeverity.BLOCKING, 5),
                        defect(vehicle, "luces", "Luces", DefectSeverity.NON_BLOCKING, 0)));

        assertEquals(1, service.alertIfRecurring(vehicle.getId(), "AB123CD", List.of("luces")));
    }

    @Test
    void conDosFallasNoAvisa() throws Exception {
        when(defectRepository.findReportedSinceByVehicleIdWithInspection(vehicle.getId(), WINDOW_START))
                .thenReturn(List.of(brakes(vehicle, 10), brakes(vehicle, 0)));

        assertEquals(0, service.alertIfRecurring(vehicle.getId(), "AB123CD", List.of("frenos")));

        verify(alertRepository, never()).save(any());
        verify(notificationService, never()).notifyConfiguredRecipients(any(), any(), any(), any());
    }

    @Test
    void miraSoloLosUltimos30DiasDelVehiculo() {
        // Tres fallas en 45 días: la consulta trae solo las de la ventana, que no llegan a 3.
        when(defectRepository.findReportedSinceByVehicleIdWithInspection(any(), any())).thenReturn(List.of());

        service.alertIfRecurring(vehicle.getId(), "AB123CD", List.of("frenos"));

        verify(defectRepository).findReportedSinceByVehicleIdWithInspection(vehicle.getId(), WINDOW_START);
    }

    @Test
    void tresFallasDeOtrosControlesNoSumanParaElQueFallo() throws Exception {
        when(defectRepository.findReportedSinceByVehicleIdWithInspection(vehicle.getId(), WINDOW_START))
                .thenReturn(List.of(brakes(vehicle, 10), defect(vehicle, "luces", "Luces", DefectSeverity.BLOCKING, 5),
                        defect(vehicle, "neumaticos", "Neumáticos", DefectSeverity.BLOCKING, 0)));

        assertEquals(0, service.alertIfRecurring(vehicle.getId(), "AB123CD", List.of("neumaticos")));
    }

    @Test
    void unCuartoDefectoDentroDeLaVentanaNoRepiteElAviso() throws Exception {
        when(defectRepository.findReportedSinceByVehicleIdWithInspection(vehicle.getId(), WINDOW_START))
                .thenReturn(List.of(brakes(vehicle, 20), brakes(vehicle, 10), brakes(vehicle, 5), brakes(vehicle, 0)));
        when(alertRepository.existsByVehicleIdAndItemIdAndSentAtAfter(vehicle.getId(), "frenos", WINDOW_START))
                .thenReturn(true);

        assertEquals(0, service.alertIfRecurring(vehicle.getId(), "AB123CD", List.of("frenos")));

        verify(alertRepository, never()).save(any());
        verify(notificationService, never()).notifyConfiguredRecipients(any(), any(), any(), any());
    }

    @Test
    void siElAvisoFallaNoCuentaComoMandadoYNoRompeElResto() throws Exception {
        when(defectRepository.findReportedSinceByVehicleIdWithInspection(vehicle.getId(), WINDOW_START))
                .thenReturn(List.of(brakes(vehicle, 20), brakes(vehicle, 10), brakes(vehicle, 0),
                        defect(vehicle, "luces", "Luces", DefectSeverity.BLOCKING, 20),
                        defect(vehicle, "luces", "Luces", DefectSeverity.BLOCKING, 10),
                        defect(vehicle, "luces", "Luces", DefectSeverity.BLOCKING, 0)));
        doThrow(new IllegalStateException("bandeja caída")).when(notificationService)
                .notifyConfiguredRecipients(eq(NotificationType.RECURRING_DEFECT), eq("Defecto recurrente en AB123CD"),
                        eq("Frenos de AB123CD: 3 defectos en 30 días."), anyString());

        // El de frenos falla (su marca se deshace con la transacción); el de luces sale igual.
        assertEquals(1, service.alertIfRecurring(vehicle.getId(), "AB123CD", List.of("frenos", "luces")));

        verify(notificationService).notifyConfiguredRecipients(NotificationType.RECURRING_DEFECT,
                "Defecto recurrente en AB123CD", "Luces de AB123CD: 3 defectos en 30 días.",
                "/vehiculos/" + vehicle.getId());
    }

    @Test
    void sinNombreGuardadoUsaElIdDelControl() throws Exception {
        when(defectRepository.findReportedSinceByVehicleIdWithInspection(vehicle.getId(), WINDOW_START))
                .thenReturn(List.of(defect(vehicle, "frenos", null, DefectSeverity.BLOCKING, 20),
                        defect(vehicle, "frenos", null, DefectSeverity.BLOCKING, 10),
                        defect(vehicle, "frenos", null, DefectSeverity.BLOCKING, 0)));

        service.alertIfRecurring(vehicle.getId(), "AB123CD", List.of("frenos"));

        verify(notificationService).notifyConfiguredRecipients(NotificationType.RECURRING_DEFECT,
                "Defecto recurrente en AB123CD", "frenos de AB123CD: 3 defectos en 30 días.",
                "/vehiculos/" + vehicle.getId());
    }

    // --- listado ---

    @Test
    void elListadoAgrupaPorVehiculoYControlYDejaAfueraLosQueNoLleganAlUmbral() throws Exception {
        List<Defect> defects = new ArrayList<>(List.of(brakes(vehicle, 20), brakes(vehicle, 10), brakes(vehicle, 1)));
        // El mismo control en otro vehículo no suma al primero: con 2 no llega.
        defects.add(brakes(otherVehicle, 3));
        defects.add(brakes(otherVehicle, 2));
        // Cuatro fallas de luces en el segundo vehículo: va primero por cantidad.
        for (long day : new long[]{25, 15, 8, 4}) {
            defects.add(defect(otherVehicle, "luces", "Luces", DefectSeverity.NON_BLOCKING, day));
        }
        when(defectRepository.findReportedSinceWithInspection(WINDOW_START)).thenReturn(defects);

        RecurringDefectsDto result = service.list(null);

        assertEquals(3, result.threshold());
        assertEquals(30, result.windowDays());
        assertEquals(2, result.items().size());
        RecurringDefectsDto.Item lights = result.items().get(0);
        assertEquals("AC456EF", lights.plate());
        assertEquals("luces", lights.itemId());
        assertEquals(4, lights.count());
        RecurringDefectsDto.Item brakes = result.items().get(1);
        assertEquals(vehicle.getId().toString(), brakes.vehicleId());
        assertEquals("AB123CD", brakes.plate());
        assertEquals("Frenos", brakes.itemLabel());
        assertEquals(3, brakes.count());
        assertEquals(NOW.minus(Duration.ofDays(1)), brakes.lastReportedAt());
        // Más reciente primero, con lo que hace falta para mostrar cada falla.
        assertEquals(NOW.minus(Duration.ofDays(1)), brakes.occurrences().get(0).reportedAt());
        assertEquals(NOW.minus(Duration.ofDays(20)), brakes.occurrences().get(2).reportedAt());
        assertEquals("blocking", brakes.occurrences().get(0).severity());
        assertEquals("open", brakes.occurrences().get(0).status());
    }

    @Test
    void conElMismoConteoVaPrimeroElDeLaFallaMasReciente() throws Exception {
        List<Defect> defects = new ArrayList<>(List.of(brakes(vehicle, 20), brakes(vehicle, 10), brakes(vehicle, 9)));
        defects.addAll(List.of(brakes(otherVehicle, 20), brakes(otherVehicle, 10), brakes(otherVehicle, 0)));
        when(defectRepository.findReportedSinceWithInspection(WINDOW_START)).thenReturn(defects);

        RecurringDefectsDto result = service.list(null);

        assertEquals("AC456EF", result.items().get(0).plate());
        assertEquals("AB123CD", result.items().get(1).plate());
    }

    @Test
    void elListadoDeUnVehiculoFiltraPorEseVehiculo() throws Exception {
        when(defectRepository.findReportedSinceByVehicleIdWithInspection(vehicle.getId(), WINDOW_START))
                .thenReturn(List.of(brakes(vehicle, 20), brakes(vehicle, 10), brakes(vehicle, 0)));

        RecurringDefectsDto result = service.list(vehicle.getId().toString());

        assertEquals(1, result.items().size());
        verify(defectRepository, never()).findReportedSinceWithInspection(any());
    }

    @Test
    void unIdDeVehiculoMalFormadoDevuelveLaListaVacia() {
        RecurringDefectsDto result = service.list("abc");

        assertTrue(result.items().isEmpty());
        verify(defectRepository, never()).findReportedSinceByVehicleIdWithInspection(any(), any());
    }
}
