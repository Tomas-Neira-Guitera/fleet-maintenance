package org.fleetguard.service;

import org.fleetguard.entity.Defect;
import org.fleetguard.entity.NotificationChannel;
import org.fleetguard.entity.NotificationType;
import org.fleetguard.entity.Role;
import org.fleetguard.entity.UnattendedDefectAlert;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.repository.DefectRepository;
import org.fleetguard.repository.ScheduledMaintenanceRepository;
import org.fleetguard.repository.UnattendedDefectAlertRepository;
import org.fleetguard.repository.VehicleRepository;
import org.fleetguard.repository.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.fleetguard.service.UnattendedDefectsTest.NOW;
import static org.fleetguard.service.UnattendedDefectsTest.daysAgo;
import static org.fleetguard.service.UnattendedDefectsTest.defect;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CAM-142: la corrida diaria manda un aviso por vehículo con sus defectos sin atender, y cada defecto se
 * avisa una sola vez. El registro de avisados se simula en memoria.
 */
class UnattendedDefectAlertServiceTest {

    private final DefectRepository defectRepository = mock(DefectRepository.class);
    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final UnattendedDefectAlertRepository alertRepository = mock(UnattendedDefectAlertRepository.class);
    private final WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
    private final NotificationSettingsService settings = mock(NotificationSettingsService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final UnattendedDefectAlertService service = new UnattendedDefectAlertService(defectRepository,
            vehicleRepository, alertRepository,
            new UnattendedDefects(workOrderRepository, mock(ScheduledMaintenanceRepository.class), Clock.fixed(NOW, ZoneOffset.UTC), 7),
            settings, notificationService, new NotificationDispatcher(mock(PlatformTransactionManager.class)),
            Clock.fixed(NOW, ZoneOffset.UTC));

    private final List<UUID> alerted = new ArrayList<>();
    private Vehicle truck;
    private Vehicle van;

    private static Vehicle vehicle(String plate) throws Exception {
        Vehicle vehicle = new Vehicle(plate, "Iveco", "Daily");
        Field id = Vehicle.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(vehicle, UUID.randomUUID());
        return vehicle;
    }

    private void enabled(boolean on) {
        when(settings.effective(NotificationType.DEFECT_UNATTENDED)).thenReturn(new EffectiveNotificationSetting(
                NotificationType.DEFECT_UNATTENDED, on, Set.of(NotificationChannel.IN_APP), Set.of(Role.ADMIN),
                Set.of(), null, null, false));
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        truck = vehicle("AB123CD");
        van = vehicle("AF102ZT");
        when(vehicleRepository.findAllById(any())).thenReturn(List.of(truck, van));
        enabled(true);
        when(alertRepository.findAlertedDefectIds(any())).thenAnswer(invocation -> {
            Collection<UUID> ids = invocation.getArgument(0);
            return alerted.stream().filter(ids::contains).toList();
        });
        when(alertRepository.saveAllAndFlush(any())).thenAnswer(invocation -> {
            Iterable<UnattendedDefectAlert> logs = invocation.getArgument(0);
            logs.forEach(log -> alerted.add(log.getDefectId()));
            return logs;
        });
    }

    @Test
    void dosDefectosSinAtenderDeUnVehiculoVanEnUnSoloAvisoQueDiceQueSonDos() throws Exception {
        Defect light = defect(truck.getId(), "Luz de freno", daysAgo(12));
        Defect mirror = defect(truck.getId(), "Espejo roto", daysAgo(9));
        when(defectRepository.findAllWithInspection()).thenReturn(List.of(mirror, light));

        assertEquals(1, service.checkAll());

        verify(notificationService).notifyConfiguredRecipients(NotificationType.DEFECT_UNATTENDED,
                "2 defectos sin atender en AB123CD",
                "2 defectos llevan más de 7 días sin OT ni turno: Luz de freno; Espejo roto.",
                "/vehiculos/" + truck.getId());
        assertEquals(Set.of(light.getId(), mirror.getId()), Set.copyOf(alerted));
    }

    @Test
    void unDefectoSoloDiceCuantosDiasLleva() throws Exception {
        when(defectRepository.findAllWithInspection()).thenReturn(List.of(defect(van.getId(), "Bocina", daysAgo(8))));

        assertEquals(1, service.checkAll());

        verify(notificationService).notifyConfiguredRecipients(NotificationType.DEFECT_UNATTENDED,
                "Defecto sin atender en AF102ZT", "«Bocina» lleva 8 días sin OT ni turno.", "/vehiculos/" + van.getId());
    }

    @Test
    void losDefectosDelMismoNombreSeJuntanConLaCantidad() throws Exception {
        // El mismo control reportado en varias inspecciones: cuentan todos, pero el nombre va una vez.
        when(defectRepository.findAllWithInspection()).thenReturn(List.of(
                defect(van.getId(), "Foco trasero quemado", daysAgo(14)), defect(van.getId(), "Bocina", daysAgo(12)),
                defect(van.getId(), "Foco trasero quemado", daysAgo(10))));

        assertEquals(1, service.checkAll());

        verify(notificationService).notifyConfiguredRecipients(NotificationType.DEFECT_UNATTENDED,
                "3 defectos sin atender en AF102ZT",
                "3 defectos llevan más de 7 días sin OT ni turno: Foco trasero quemado (2 veces); Bocina.",
                "/vehiculos/" + van.getId());
    }

    @Test
    void cadaVehiculoTieneSuAviso() throws Exception {
        when(defectRepository.findAllWithInspection()).thenReturn(List.of(
                defect(truck.getId(), "Luz de freno", daysAgo(10)), defect(van.getId(), "Bocina", daysAgo(10))));

        assertEquals(2, service.checkAll());
    }

    @Test
    void alDiaSiguienteNoSeRepiteElAvisoDelMismoDefecto() throws Exception {
        Defect light = defect(truck.getId(), "Luz de freno", daysAgo(10));
        when(defectRepository.findAllWithInspection()).thenReturn(List.of(light));
        assertEquals(1, service.checkAll());

        // Al otro día aparece otro defecto en el mismo vehículo: el aviso nuevo es solo por ese.
        Defect mirror = defect(truck.getId(), "Espejo roto", daysAgo(8));
        when(defectRepository.findAllWithInspection()).thenReturn(List.of(light, mirror));
        assertEquals(1, service.checkAll());
        assertEquals(0, service.checkAll());

        verify(notificationService).notifyConfiguredRecipients(NotificationType.DEFECT_UNATTENDED,
                "Defecto sin atender en AB123CD", "«Espejo roto» lleva 8 días sin OT ni turno.", "/vehiculos/" + truck.getId());
    }

    @Test
    void unDefectoAtendidoNoAvisa() throws Exception {
        Defect light = defect(truck.getId(), "Luz de freno", daysAgo(10));
        when(defectRepository.findAllWithInspection()).thenReturn(List.of(light));
        when(workOrderRepository.findDefectIdsWithStatusIn(any())).thenReturn(List.of(light.getId()));

        assertEquals(0, service.checkAll());
        verify(notificationService, never()).notifyConfiguredRecipients(any(), anyString(), anyString(), anyString());
    }

    @Test
    void unVehiculoDadoDeBajaNoAvisaNiAnota() throws Exception {
        truck.setActive(false);
        when(defectRepository.findAllWithInspection()).thenReturn(List.of(defect(truck.getId(), "Luz de freno", daysAgo(10))));

        assertEquals(0, service.checkAll());
        assertEquals(List.of(), alerted);
    }

    @Test
    void conElAvisoApagadoNoSaleNadaNiSeAnota() throws Exception {
        enabled(false);
        when(defectRepository.findAllWithInspection()).thenReturn(List.of(defect(truck.getId(), "Luz de freno", daysAgo(10))));

        assertEquals(0, service.checkAll());
        verify(notificationService, never()).notifyConfiguredRecipients(any(), anyString(), anyString(), anyString());
        assertEquals(List.of(), alerted);
    }

    @Test
    void unaFallaAlAvisarNoCuentaComoEnviadoNiCortaLaCorrida() throws Exception {
        // La marca y el aviso van en la misma transacción (NotificationDispatcher): si el aviso falla, la marca
        // se deshace y el defecto se reintenta al otro día. Acá no hay base, así que solo se ve que la corrida
        // sigue con el otro vehículo y no lo cuenta como enviado.
        when(defectRepository.findAllWithInspection()).thenReturn(List.of(
                defect(truck.getId(), "Luz de freno", daysAgo(10)), defect(van.getId(), "Bocina", daysAgo(10))));
        doThrow(new IllegalStateException("mail caído")).when(notificationService)
                .notifyConfiguredRecipients(eq(NotificationType.DEFECT_UNATTENDED), eq("Defecto sin atender en AB123CD"),
                        anyString(), anyString());

        assertEquals(1, service.checkAll());
    }
}
