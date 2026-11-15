package org.fleetguard.event;

import org.fleetguard.dto.BlockReasonDto;
import org.fleetguard.entity.NotificationType;
import org.fleetguard.entity.Trip;
import org.fleetguard.entity.TripStatus;
import org.fleetguard.entity.WorkOrder;
import org.fleetguard.repository.DefectRepository;
import org.fleetguard.repository.NotificationRepository;
import org.fleetguard.repository.TripRepository;
import org.fleetguard.repository.WorkOrderRepository;
import org.fleetguard.service.NotificationDispatcher;
import org.fleetguard.service.NotificationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Avisos al chofer sobre la disponibilidad del vehículo (CAM-102/103), después del commit.
 * <ul>
 *   <li>No disponible: solo si lo bloqueó un documento vencido, al chofer que lo tiene en viaje. Si lo
 *       bloqueó un defecto, lo reportó el propio chofer y ya lo sabe.</li>
 *   <li>Disponible de nuevo: a quienes reportaron los bloqueantes que se resolvieron y a quienes se les
 *       avisó del bloqueo.</li>
 * </ul>
 * El link lleva a la inspección del vehículo (CAM-133).
 */
@Component
public class VehicleAvailabilityNotificationListener {

    private final TripRepository tripRepository;
    private final DefectRepository defectRepository;
    private final NotificationRepository notificationRepository;
    private final WorkOrderRepository workOrderRepository;
    private final NotificationService notificationService;
    private final NotificationDispatcher dispatcher;

    public VehicleAvailabilityNotificationListener(TripRepository tripRepository, DefectRepository defectRepository,
                                                   NotificationRepository notificationRepository,
                                                   WorkOrderRepository workOrderRepository,
                                                   NotificationService notificationService,
                                                   NotificationDispatcher dispatcher) {
        this.tripRepository = tripRepository;
        this.defectRepository = defectRepository;
        this.notificationRepository = notificationRepository;
        this.workOrderRepository = workOrderRepository;
        this.notificationService = notificationService;
        this.dispatcher = dispatcher;
    }

    static String link(UUID vehicleId) {
        return "/inspeccion/" + vehicleId;
    }

    @TransactionalEventListener
    public void onAvailabilityChanged(VehicleAvailabilityChangedEvent event) {
        if (event.available()) {
            notifyAvailable(event);
        } else {
            notifyUnavailable(event);
        }
    }

    private void notifyUnavailable(VehicleAvailabilityChangedEvent event) {
        List<String> documents = event.reasons().stream()
                .filter(r -> BlockReasonDto.EXPIRED_DOCUMENT.equals(r.type()))
                .map(BlockReasonDto::message)
                .toList();
        if (documents.isEmpty()) {
            return;
        }
        tripRepository.findFirstByVehicle_IdAndStatus(event.vehicleId(), TripStatus.OPEN)
                .map(Trip::getOpenedById)
                .ifPresent(driverId -> dispatcher.dispatch("vehículo no disponible " + event.plate(), () ->
                        notificationService.notify(driverId, NotificationType.VEHICLE_UNAVAILABLE,
                                "Tu vehículo no está disponible",
                                event.plate() + " quedó no disponible (" + String.join("; ", documents)
                                        + "). Cuando cierres el viaje no lo vas a poder volver a sacar hasta que se renueve.",
                                link(event.vehicleId()))));
    }

    private void notifyAvailable(VehicleAvailabilityChangedEvent event) {
        if (event.unavailableSince() == null) {
            return;
        }
        Set<UUID> recipients = new LinkedHashSet<>();
        defectRepository.findReportersOfBlockingResolvedSince(event.vehicleId(), event.unavailableSince()).stream()
                .map(VehicleAvailabilityNotificationListener::parse).filter(Objects::nonNull).forEach(recipients::add);
        recipients.addAll(notificationRepository.findRecipientIds(NotificationType.VEHICLE_UNAVAILABLE,
                link(event.vehicleId()), event.unavailableSince()));
        if (recipients.isEmpty()) {
            return;
        }
        String message = event.plate() + " ya puede salir." + resolvedBy(event.workOrderId());
        for (UUID recipient : recipients) {
            dispatcher.dispatch("vehículo disponible " + event.plate(), () ->
                    notificationService.notify(recipient, NotificationType.VEHICLE_AVAILABLE,
                            "Tu vehículo vuelve a estar disponible", message, link(event.vehicleId())));
        }
    }

    /** " Se resolvió con la orden de trabajo «Pérdida de aire»." si lo liberó una OT. */
    private String resolvedBy(UUID workOrderId) {
        if (workOrderId == null) {
            return "";
        }
        return workOrderRepository.findById(workOrderId).map(WorkOrder::getTitle)
                .map(title -> " Se resolvió con la orden de trabajo «" + title + "».").orElse("");
    }

    private static UUID parse(String raw) {
        try {
            return raw == null ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
