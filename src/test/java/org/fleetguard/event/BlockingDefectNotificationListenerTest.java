package org.fleetguard.event;

import org.fleetguard.entity.InspectionType;
import org.fleetguard.entity.NotificationType;
import org.fleetguard.service.NotificationDispatcher;
import org.fleetguard.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** CAM-97: un aviso por inspección con defectos bloqueantes, que lleva al vehículo. */
class BlockingDefectNotificationListenerTest {

    private final NotificationService notificationService = mock(NotificationService.class);
    private final BlockingDefectNotificationListener listener = new BlockingDefectNotificationListener(notificationService,
            new NotificationDispatcher(mock(PlatformTransactionManager.class)));
    private final UUID vehicleId = UUID.randomUUID();

    @Test
    void unDefectoBloqueante() {
        listener.onBlockingDefect(new BlockingDefectReportedEvent(vehicleId, "AB123CD", InspectionType.PRE_TRIP, "chofer1",
                List.of("Neumático cortado")));

        verify(notificationService).notifyConfiguredRecipients(NotificationType.BLOCKING_DEFECT_REPORTED,
                "Defecto bloqueante en AB123CD",
                "Neumático cortado. Reportado en el pre-viaje por chofer1. El vehículo no puede salir hasta resolverlo.",
                "/vehiculos/" + vehicleId);
    }

    @Test
    void variosBloqueantesVanEnUnSoloAviso() {
        listener.onBlockingDefect(new BlockingDefectReportedEvent(vehicleId, "AB123CD", InspectionType.POST_TRIP, null,
                List.of("Frenos", "Luces")));

        verify(notificationService).notifyConfiguredRecipients(NotificationType.BLOCKING_DEFECT_REPORTED,
                "2 defectos bloqueantes en AB123CD",
                "Frenos; Luces. Reportado en el post-viaje. El vehículo no puede salir hasta resolverlo.",
                "/vehiculos/" + vehicleId);
    }

    @Test
    void unErrorAlAvisarNoSePropaga() {
        doThrow(new IllegalStateException("caído")).when(notificationService)
                .notifyConfiguredRecipients(any(), any(), any(), any());

        listener.onBlockingDefect(new BlockingDefectReportedEvent(vehicleId, "AB123CD", InspectionType.PRE_TRIP, "c",
                List.of("Frenos")));
    }
}
