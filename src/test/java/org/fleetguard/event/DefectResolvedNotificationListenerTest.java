package org.fleetguard.event;

import org.fleetguard.entity.CheckOutcome;
import org.fleetguard.entity.Defect;
import org.fleetguard.entity.DefectSeverity;
import org.fleetguard.entity.Inspection;
import org.fleetguard.entity.InspectionAnswer;
import org.fleetguard.entity.InspectionType;
import org.fleetguard.entity.NotificationType;
import org.fleetguard.entity.Role;
import org.fleetguard.entity.User;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.WorkOrder;
import org.fleetguard.repository.DefectRepository;
import org.fleetguard.repository.UserRepository;
import org.fleetguard.repository.VehicleRepository;
import org.fleetguard.repository.WorkOrderRepository;
import org.fleetguard.service.NotificationDispatcher;
import org.fleetguard.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CAM-141: al chofer que reportó un defecto se le avisa cuando se finaliza su OT, salvo que ya reciba
 * "Vehículo disponible de nuevo" (CAM-103) o que no se sepa quién es.
 */
class DefectResolvedNotificationListenerTest {

    private static final String TITLE = "Se resolvió un defecto que reportaste";

    private final DefectRepository defectRepository = mock(DefectRepository.class);
    private final WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final DefectResolvedNotificationListener listener = new DefectResolvedNotificationListener(
            defectRepository, workOrderRepository, vehicleRepository, userRepository, notificationService,
            new NotificationDispatcher(mock(PlatformTransactionManager.class)));

    private final UUID vehicleId = UUID.randomUUID();
    private final UUID driverId = UUID.randomUUID();
    private final UUID defectId = UUID.randomUUID();
    private final UUID workOrderId = UUID.randomUUID();
    private final WorkOrder workOrder = mock(WorkOrder.class);

    @BeforeEach
    void setUp() {
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(new Vehicle("AF102ZT", "Iveco", "Daily")));
        when(userRepository.findById(driverId)).thenReturn(Optional.of(new User("carlos.gomez", "hash", Role.CHOFER)));
        when(workOrder.getClosingDescription()).thenReturn("Lámpara reemplazada");
        when(workOrderRepository.findById(workOrderId)).thenReturn(Optional.of(workOrder));
    }

    private void defect(DefectSeverity severity, String reporter) {
        Inspection inspection = new Inspection(null, vehicleId, reporter, "carlos.gomez", InspectionType.PRE_TRIP,
                Instant.now(), null, null, severity == DefectSeverity.BLOCKING);
        InspectionAnswer answer = new InspectionAnswer("luces-freno", "Luz de freno", CheckOutcome.DEFECT, null);
        inspection.addAnswer(answer);
        Defect defect = new Defect(severity, "Luz de freno", null, Instant.now());
        answer.attachDefect(defect);
        when(defectRepository.findByIdWithInspection(defectId)).thenReturn(Optional.of(defect));
    }

    private void resolved(boolean vehicleBecameAvailable) {
        listener.onDefectResolved(new DefectResolvedEvent(defectId, workOrderId, vehicleBecameAvailable));
    }

    @Test
    void alResolverseUnDefectoNoBloqueanteLeAvisaAQuienLoReporto() {
        defect(DefectSeverity.NON_BLOCKING, driverId.toString());

        resolved(false);

        verify(notificationService).notify(driverId, NotificationType.DEFECT_RESOLVED, TITLE,
                "AF102ZT: se resolvió «Luz de freno». Lámpara reemplazada.", "/");
    }

    @Test
    void siElBloqueanteLiberoAlVehiculoSoloLlegaVehiculoDisponible() {
        defect(DefectSeverity.BLOCKING, driverId.toString());

        resolved(true);

        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }

    @Test
    void siQuedanOtrosBloqueantesElAvisoSale() {
        defect(DefectSeverity.BLOCKING, driverId.toString());

        resolved(false);

        verify(notificationService).notify(driverId, NotificationType.DEFECT_RESOLVED, TITLE,
                "AF102ZT: se resolvió «Luz de freno». Lámpara reemplazada.", "/");
    }

    @Test
    void unNoBloqueanteAvisaAunqueElVehiculoSeHayaLiberado() {
        // Quien reportó un no bloqueante no está entre los que reciben VEHICLE_AVAILABLE.
        defect(DefectSeverity.NON_BLOCKING, driverId.toString());

        resolved(true);

        verify(notificationService).notify(driverId, NotificationType.DEFECT_RESOLVED, TITLE,
                "AF102ZT: se resolvió «Luz de freno». Lámpara reemplazada.", "/");
    }

    @Test
    void conUnDriverIdViejoOUnChoferQueYaNoExisteNoAvisa() {
        defect(DefectSeverity.NON_BLOCKING, "drv-001");
        resolved(false);

        defect(DefectSeverity.NON_BLOCKING, UUID.randomUUID().toString());
        resolved(false);

        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }

    @Test
    void elCierreSinPuntoFinalLoSumaYSinCierreQuedaSoloElDefecto() {
        defect(DefectSeverity.NON_BLOCKING, driverId.toString());

        when(workOrder.getClosingDescription()).thenReturn("Se cambió la lámpara!");
        resolved(false);
        when(workOrderRepository.findById(workOrderId)).thenReturn(Optional.empty());
        resolved(false);

        verify(notificationService).notify(driverId, NotificationType.DEFECT_RESOLVED, TITLE,
                "AF102ZT: se resolvió «Luz de freno». Se cambió la lámpara!", "/");
        verify(notificationService).notify(driverId, NotificationType.DEFECT_RESOLVED, TITLE,
                "AF102ZT: se resolvió «Luz de freno».", "/");
    }

    @Test
    void unaFallaAlAvisarNoLeLlegaAQuienFinalizoLaOt() {
        defect(DefectSeverity.NON_BLOCKING, driverId.toString());
        doThrow(new IllegalStateException("push caído")).when(notificationService)
                .notify(any(), any(), any(), any(), any());

        assertDoesNotThrow(() -> resolved(false));
    }
}
