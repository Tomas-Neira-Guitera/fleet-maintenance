package org.fleetguard.service;

import org.fleetguard.entity.IntervalType;
import org.fleetguard.entity.MaintenanceAlertLog;
import org.fleetguard.entity.MaintenanceAlertStage;
import org.fleetguard.entity.MaintenancePlan;
import org.fleetguard.entity.NotificationChannel;
import org.fleetguard.entity.NotificationType;
import org.fleetguard.entity.Role;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.VehicleMaintenanceAssignment;
import org.fleetguard.repository.MaintenanceAlertLogRepository;
import org.fleetguard.repository.VehicleMaintenanceAssignmentRepository;
import org.fleetguard.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CAM-118: un aviso al entrar en la anticipación y otro al vencer, una vez por ciclo. */
class MaintenanceAlertServiceTest {

    // 12:00 UTC = 09:00 en Argentina: "hoy" es el 23/10.
    private static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);

    private final VehicleMaintenanceAssignmentRepository assignmentRepository = mock(VehicleMaintenanceAssignmentRepository.class);
    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final MaintenanceAlertLogRepository logRepository = mock(MaintenanceAlertLogRepository.class);
    private final PlanNotificationSettingsService planSettings = mock(PlanNotificationSettingsService.class);
    private final NotificationSettingsService settings = mock(NotificationSettingsService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final LinkedDocumentDueDates linkedDueDates = mock(LinkedDocumentDueDates.class);
    private final MaintenanceAlertService service = new MaintenanceAlertService(assignmentRepository, vehicleRepository,
            logRepository, planSettings, settings, notificationService,
            new NotificationDispatcher(mock(PlatformTransactionManager.class)), linkedDueDates, Clock.fixed(NOW, ZoneOffset.UTC));

    private Vehicle vehicle;
    private MaintenancePlan timePlan;

    private static void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    private VehicleMaintenanceAssignment assignment(MaintenancePlan plan, Long nextDueKm, LocalDate nextDueDate) throws Exception {
        VehicleMaintenanceAssignment a = new VehicleMaintenanceAssignment(vehicle.getId(), plan, null, null);
        setId(a, UUID.randomUUID());
        a.setNextDueKm(nextDueKm);
        a.setNextDueDate(nextDueDate);
        return a;
    }

    private void general(boolean dueSoonEnabled, boolean overdueEnabled, Integer leadDays, Integer leadKm) {
        when(settings.effective(NotificationType.MAINTENANCE_DUE_SOON)).thenReturn(new EffectiveNotificationSetting(
                NotificationType.MAINTENANCE_DUE_SOON, dueSoonEnabled, Set.of(NotificationChannel.IN_APP), Set.of(Role.ADMIN),
                Set.of(), leadDays, leadKm, false));
        when(settings.effective(NotificationType.MAINTENANCE_OVERDUE)).thenReturn(new EffectiveNotificationSetting(
                NotificationType.MAINTENANCE_OVERDUE, overdueEnabled, Set.of(NotificationChannel.IN_APP), Set.of(Role.ADMIN),
                Set.of(), null, null, false));
    }

    @BeforeEach
    void setUp() throws Exception {
        vehicle = new Vehicle("AB123CD", "Ford", "Cargo");
        setId(vehicle, UUID.randomUUID());
        vehicle.setOdometerKm(19_500);
        when(vehicleRepository.findAllById(any())).thenReturn(List.of(vehicle));
        timePlan = new MaintenancePlan("Aceite", null, IntervalType.TIME, null, 90);
        setId(timePlan, UUID.randomUUID());
        when(planSettings.alertConfig(any())).thenReturn(new PlanNotificationSettingsService.AlertConfig(false, false, null, null, null));
        general(true, true, 7, 1000);
    }

    @Test
    void avisaUnaVezAlEntrarEnLaAnticipacion() throws Exception {
        VehicleMaintenanceAssignment a = assignment(timePlan, null, TODAY.plusDays(5));
        when(assignmentRepository.findByActiveTrue()).thenReturn(List.of(a));

        assertEquals(1, service.checkAll());

        ArgumentCaptor<MaintenanceAlertLog> log = ArgumentCaptor.forClass(MaintenanceAlertLog.class);
        verify(logRepository).saveAndFlush(log.capture());
        assertEquals(MaintenanceAlertStage.DUE_SOON, log.getValue().getStage());
        verify(notificationService).notifyConfiguredRecipients(eq(NotificationType.MAINTENANCE_DUE_SOON),
                eq("Mantenimiento por vencer"), eq("Aceite de AB123CD: faltan 5 días (vence el 28/10/2026)."),
                eq("/vehiculos/" + vehicle.getId()), isNull());
    }

    @Test
    void siYaSeAvisoEnEsteCicloNoRepite() throws Exception {
        VehicleMaintenanceAssignment a = assignment(timePlan, null, TODAY.plusDays(5));
        when(assignmentRepository.findByActiveTrue()).thenReturn(List.of(a));
        when(logRepository.existsByAssignmentIdAndStageAndCycleKey(eq(a.getId()), eq(MaintenanceAlertStage.DUE_SOON), anyString()))
                .thenReturn(true);

        assertEquals(0, service.checkAll());

        verify(notificationService, never()).notifyConfiguredRecipients(any(), any(), any(), any(), any());
    }

    @Test
    void alVencerMandaElSegundoAviso() throws Exception {
        VehicleMaintenanceAssignment a = assignment(timePlan, null, TODAY.minusDays(2));
        when(assignmentRepository.findByActiveTrue()).thenReturn(List.of(a));

        assertEquals(1, service.checkAll());

        verify(notificationService).notifyConfiguredRecipients(eq(NotificationType.MAINTENANCE_OVERDUE),
                eq("Mantenimiento vencido"), anyString(), anyString(), isNull());
    }

    @Test
    void fueraDeLaAnticipacionNoAvisa() throws Exception {
        when(assignmentRepository.findByActiveTrue()).thenReturn(List.of(assignment(timePlan, null, TODAY.plusDays(30))));

        assertEquals(0, service.checkAll());
    }

    @Test
    void conElTipoApagadoNoAvisa() throws Exception {
        general(false, true, 7, 1000);
        when(assignmentRepository.findByActiveTrue()).thenReturn(List.of(assignment(timePlan, null, TODAY.plusDays(5))));

        assertEquals(0, service.checkAll());
    }

    @Test
    void unPlanConConfiguracionPropiaUsaSuAnticipacionYSusCanales() throws Exception {
        when(planSettings.alertConfig(timePlan.getId())).thenReturn(new PlanNotificationSettingsService.AlertConfig(
                true, true, Set.of(NotificationChannel.EMAIL), 30, null));
        general(false, false, 7, 1000);
        when(assignmentRepository.findByActiveTrue()).thenReturn(List.of(assignment(timePlan, null, TODAY.plusDays(20))));

        assertEquals(1, service.checkAll());

        verify(notificationService).notifyConfiguredRecipients(eq(NotificationType.MAINTENANCE_DUE_SOON), anyString(),
                anyString(), anyString(), eq(Set.of(NotificationChannel.EMAIL)));
    }

    @Test
    void unPlanConAvisosApagadosNoAvisaAunqueLoGeneralEsteActivo() throws Exception {
        when(planSettings.alertConfig(timePlan.getId())).thenReturn(new PlanNotificationSettingsService.AlertConfig(
                true, false, Set.of(), 7, null));
        when(assignmentRepository.findByActiveTrue()).thenReturn(List.of(assignment(timePlan, null, TODAY.minusDays(1))));

        assertEquals(0, service.checkAll());
    }

    @Test
    void alSubirElKmRevisaLosPlanesDelVehiculo() throws Exception {
        MaintenancePlan kmPlan = new MaintenancePlan("Frenos", null, IntervalType.KM, 20_000, null);
        setId(kmPlan, UUID.randomUUID());
        when(assignmentRepository.findByVehicleIdAndActive(vehicle.getId(), true))
                .thenReturn(List.of(assignment(kmPlan, 20_000L, null)));

        assertEquals(1, service.checkVehicle(vehicle.getId()));

        verify(notificationService).notifyConfiguredRecipients(eq(NotificationType.MAINTENANCE_DUE_SOON), anyString(),
                eq("Frenos de AB123CD: faltan 500 km (vence a los 20.000 km)."), anyString(), isNull());
    }

    @Test
    void unVehiculoDadoDeBajaOUnPlanInactivoNoAvisan() throws Exception {
        vehicle.setActive(false);
        when(assignmentRepository.findByActiveTrue()).thenReturn(List.of(assignment(timePlan, null, TODAY.minusDays(1))));

        assertEquals(0, service.checkAll());
        assertEquals(0, service.checkVehicle(UUID.randomUUID()));
    }

    @Test
    void unAvisoQueFallaNoCuentaYSeReintentaDespues() throws Exception {
        when(assignmentRepository.findByActiveTrue()).thenReturn(List.of(assignment(timePlan, null, TODAY.plusDays(5))));
        org.mockito.Mockito.doThrow(new IllegalStateException("caído")).when(notificationService)
                .notifyConfiguredRecipients(any(), any(), any(), any(), any());

        assertEquals(0, service.checkAll());
    }

    @Test
    void unPlanQueVenceConUnDocumentoCargadoNoAvisaPorSuCuenta() throws Exception {
        VehicleMaintenanceAssignment a = assignment(timePlan, null, TODAY.minusDays(2));
        when(assignmentRepository.findByActiveTrue()).thenReturn(List.of(a));
        when(linkedDueDates.documentDueDate(a)).thenReturn(Optional.of(TODAY.minusDays(2)));

        // CAM-127: el aviso sale por el documento (DocumentAlertService), no dos veces.
        assertEquals(0, service.checkAll());
        verify(notificationService, never()).notifyConfiguredRecipients(any(), any(), any(), any(), any());
    }
}
