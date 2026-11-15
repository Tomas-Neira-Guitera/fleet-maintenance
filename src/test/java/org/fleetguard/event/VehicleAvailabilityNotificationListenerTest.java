package org.fleetguard.event;

import org.fleetguard.dto.BlockReasonDto;
import org.fleetguard.entity.NotificationType;
import org.fleetguard.entity.Trip;
import org.fleetguard.entity.TripStatus;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.WorkOrder;
import org.fleetguard.repository.DefectRepository;
import org.fleetguard.repository.NotificationRepository;
import org.fleetguard.repository.TripRepository;
import org.fleetguard.repository.WorkOrderRepository;
import org.fleetguard.service.NotificationDispatcher;
import org.fleetguard.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CAM-102 / CAM-103: a qué chofer se le avisa que su vehículo no está disponible o que vuelve a estarlo.
 * El aviso de bloqueo por defecto no sale: lo reportó el propio chofer.
 */
class VehicleAvailabilityNotificationListenerTest {

    private static final Instant SINCE = Instant.parse("2026-10-20T15:00:00Z");

    private final TripRepository tripRepository = mock(TripRepository.class);
    private final DefectRepository defectRepository = mock(DefectRepository.class);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final VehicleAvailabilityNotificationListener listener = new VehicleAvailabilityNotificationListener(
            tripRepository, defectRepository, notificationRepository, workOrderRepository, notificationService,
            new NotificationDispatcher(mock(PlatformTransactionManager.class)));

    private final UUID vehicleId = UUID.randomUUID();
    private final String link = "/inspeccion/" + vehicleId;

    private VehicleAvailabilityChangedEvent blocked(BlockReasonDto... reasons) {
        return new VehicleAvailabilityChangedEvent(vehicleId, "AB123CD", false, List.of(reasons), SINCE, null);
    }

    private VehicleAvailabilityChangedEvent released(UUID workOrderId) {
        return new VehicleAvailabilityChangedEvent(vehicleId, "AB123CD", true, List.of(), SINCE, workOrderId);
    }

    @Test
    void unBloqueoPorDefectoNoLeAvisaANingunChofer() {
        listener.onAvailabilityChanged(blocked(new BlockReasonDto(BlockReasonDto.BLOCKING_DEFECT, "Defecto bloqueante: Sin frenos")));

        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }

    @Test
    void unDocumentoVencidoLeAvisaAlChoferQueLoTieneEnViaje() {
        UUID driver = UUID.randomUUID();
        Trip trip = new Trip(new Vehicle("AB123CD", "Iveco", "Daily"), SINCE, driver, "carlos.gomez");
        when(tripRepository.findFirstByVehicle_IdAndStatus(vehicleId, TripStatus.OPEN)).thenReturn(Optional.of(trip));

        listener.onAvailabilityChanged(blocked(new BlockReasonDto(BlockReasonDto.EXPIRED_DOCUMENT, "VTV / RTO: venció el 28/10/2026")));

        verify(notificationService).notify(driver, NotificationType.VEHICLE_UNAVAILABLE, "Tu vehículo no está disponible",
                "AB123CD quedó no disponible (VTV / RTO: venció el 28/10/2026). Cuando cierres el viaje no lo vas a "
                        + "poder volver a sacar hasta que se renueve.", link);
    }

    @Test
    void sinViajeAbiertoNoHayAQuienAvisarleDelBloqueo() {
        when(tripRepository.findFirstByVehicle_IdAndStatus(vehicleId, TripStatus.OPEN)).thenReturn(Optional.empty());

        listener.onAvailabilityChanged(blocked(new BlockReasonDto(BlockReasonDto.EXPIRED_DOCUMENT, "Seguro: venció el 28/10/2026")));

        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }

    @Test
    void alLiberarseLeAvisaAQuienesReportaronYAQuienesSeLesAvisoDelBloqueo() {
        UUID reporter = UUID.randomUUID();
        UUID notified = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = mock(WorkOrder.class);
        when(workOrder.getTitle()).thenReturn("Pérdida de aire en frenos");
        when(workOrderRepository.findById(workOrderId)).thenReturn(Optional.of(workOrder));
        when(defectRepository.findReportersOfBlockingResolvedSince(vehicleId, SINCE))
                .thenReturn(List.of(reporter.toString(), "drv-001", reporter.toString()));
        when(notificationRepository.findRecipientIds(NotificationType.VEHICLE_UNAVAILABLE, link, SINCE))
                .thenReturn(List.of(notified, reporter));

        listener.onAvailabilityChanged(released(workOrderId));

        String message = "AB123CD ya puede salir. Se resolvió con la orden de trabajo «Pérdida de aire en frenos».";
        verify(notificationService).notify(reporter, NotificationType.VEHICLE_AVAILABLE, "Tu vehículo vuelve a estar disponible",
                message, link);
        verify(notificationService).notify(notified, NotificationType.VEHICLE_AVAILABLE, "Tu vehículo vuelve a estar disponible",
                message, link);
    }

    @Test
    void sinNadieAQuienAvisarOSinFechaDeBloqueoNoAvisa() {
        listener.onAvailabilityChanged(released(null));
        listener.onAvailabilityChanged(new VehicleAvailabilityChangedEvent(vehicleId, "AB123CD", true, List.of(), null, null));

        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }

    @Test
    void siLoLiberoUnDocumentoElMensajeNoNombraUnaOt() {
        UUID notified = UUID.randomUUID();
        when(notificationRepository.findRecipientIds(NotificationType.VEHICLE_UNAVAILABLE, link, SINCE)).thenReturn(List.of(notified));

        listener.onAvailabilityChanged(released(null));

        verify(notificationService).notify(eq(notified), eq(NotificationType.VEHICLE_AVAILABLE), any(),
                eq("AB123CD ya puede salir."), eq(link));
    }
}
