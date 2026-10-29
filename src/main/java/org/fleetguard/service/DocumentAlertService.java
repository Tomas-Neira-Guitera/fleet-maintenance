package org.fleetguard.service;

import org.fleetguard.entity.DocumentAlertLog;
import org.fleetguard.entity.DocumentAlertStage;
import org.fleetguard.entity.NotificationType;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.VehicleDocument;
import org.fleetguard.repository.DocumentAlertLogRepository;
import org.fleetguard.repository.VehicleDocumentRepository;
import org.fleetguard.repository.VehicleRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Vencimientos de documentos (CAM-127): un aviso al entrar en la anticipación, otro el día que vence y
 * otro al vencer, una vez cada uno. Una vez por día, y al cargar o corregir un documento.
 */
@Service
public class DocumentAlertService {

    private final VehicleDocumentRepository documentRepository;
    private final VehicleRepository vehicleRepository;
    private final DocumentAlertLogRepository alertLogRepository;
    private final NotificationSettingsService settings;
    private final NotificationService notificationService;
    private final NotificationDispatcher dispatcher;
    private final VehicleAvailabilityService availabilityService;
    private final Clock clock;

    public DocumentAlertService(VehicleDocumentRepository documentRepository, VehicleRepository vehicleRepository,
                                DocumentAlertLogRepository alertLogRepository, NotificationSettingsService settings,
                                NotificationService notificationService, NotificationDispatcher dispatcher,
                                VehicleAvailabilityService availabilityService, Clock clock) {
        this.documentRepository = documentRepository;
        this.vehicleRepository = vehicleRepository;
        this.alertLogRepository = alertLogRepository;
        this.settings = settings;
        this.notificationService = notificationService;
        this.dispatcher = dispatcher;
        this.availabilityService = availabilityService;
        this.clock = clock;
    }

    /**
     * La corrida diaria: avisos y, como los documentos vencen solos con el paso del día, la disponibilidad
     * de toda la flota (CAM-102/103). Devuelve cuántos avisos mandó, para los tests.
     */
    @Scheduled(cron = "${fleetguard.document-alerts.cron:0 5 8 * * *}", zone = "America/Argentina/Buenos_Aires")
    public int checkAll() {
        int sent = check(documentRepository.findCurrentWithExpiry());
        for (Vehicle vehicle : vehicleRepository.findByActive(true)) {
            dispatcher.dispatch("disponibilidad de " + vehicle.getPlate(), () -> availabilityService.sync(vehicle.getId()));
        }
        return sent;
    }

    public int checkVehicle(UUID vehicleId) {
        return check(documentRepository.findCurrentWithExpiry().stream()
                .filter(d -> d.getVehicleId().equals(vehicleId)).toList());
    }

    private int check(List<VehicleDocument> documents) {
        if (documents.isEmpty()) {
            return 0;
        }
        LocalDate today = availabilityService.today();
        EffectiveNotificationSetting dueSoon = settings.effective(NotificationType.DOCUMENT_DUE_SOON);
        EffectiveNotificationSetting expired = settings.effective(NotificationType.DOCUMENT_EXPIRED);
        int leadDays = dueSoon.leadDays() != null ? dueSoon.leadDays() : NotificationCatalog.DEFAULT_DOCUMENT_LEAD_DAYS;
        Map<UUID, Vehicle> vehicles = vehicleRepository.findAllById(documents.stream().map(VehicleDocument::getVehicleId)
                        .distinct().toList()).stream()
                .filter(Vehicle::isActive)
                .collect(Collectors.toMap(Vehicle::getId, Function.identity()));

        int sent = 0;
        for (VehicleDocument document : documents) {
            Vehicle vehicle = vehicles.get(document.getVehicleId());
            DocumentAlertStage stage = DocumentAlertRules.stage(document.getExpiresAt(), today, leadDays);
            if (vehicle == null || stage == null) {
                continue;
            }
            boolean enabled = (stage == DocumentAlertStage.EXPIRED ? expired : dueSoon).enabled();
            if (enabled && send(document, vehicle, stage, today)) {
                sent++;
            }
        }
        return sent;
    }

    private boolean send(VehicleDocument document, Vehicle vehicle, DocumentAlertStage stage, LocalDate today) {
        if (alertLogRepository.existsByDocumentIdAndStageAndExpiresAt(document.getId(), stage, document.getExpiresAt())) {
            return false;
        }
        NotificationType type = stage == DocumentAlertStage.EXPIRED ? NotificationType.DOCUMENT_EXPIRED
                : NotificationType.DOCUMENT_DUE_SOON;
        String message = DocumentAlertRules.message(stage, document.getDisplayName(), vehicle.getPlate(),
                document.getExpiresAt(), today);
        // La marca y el aviso van juntos: si el aviso falla, se reintenta en la próxima revisión.
        boolean[] marked = {false};
        boolean committed = dispatcher.dispatch(type.toJson() + " del documento " + document.getId(), () -> {
            if (alertLogRepository.existsByDocumentIdAndStageAndExpiresAt(document.getId(), stage, document.getExpiresAt())) {
                return;
            }
            alertLogRepository.saveAndFlush(new DocumentAlertLog(document.getId(), stage, document.getExpiresAt(), clock.instant()));
            marked[0] = true;
            notificationService.notifyConfiguredRecipients(type, DocumentAlertRules.title(stage), message,
                    "/vehiculos/" + vehicle.getId() + "/documentacion");
        });
        return committed && marked[0];
    }
}
