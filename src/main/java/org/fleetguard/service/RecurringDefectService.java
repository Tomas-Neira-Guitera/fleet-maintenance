package org.fleetguard.service;

import org.fleetguard.dto.RecurringDefectsDto;
import org.fleetguard.entity.Defect;
import org.fleetguard.entity.InspectionAnswer;
import org.fleetguard.entity.NotificationType;
import org.fleetguard.entity.RecurringDefectAlert;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.repository.DefectRepository;
import org.fleetguard.repository.RecurringDefectAlertRepository;
import org.fleetguard.repository.VehicleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CAM-135: un control del checklist es recurrente cuando falla en el mismo vehículo
 * {@code threshold} veces en los últimos {@code windowDays} días. Cuentan todos los defectos,
 * bloqueantes o no y resueltos o no. Ver docs/api/CAM-135-recurring-defects-contract.md.
 */
@Service
public class RecurringDefectService {

    private final DefectRepository defectRepository;
    private final RecurringDefectAlertRepository alertRepository;
    private final VehicleRepository vehicleRepository;
    private final NotificationService notificationService;
    private final NotificationDispatcher dispatcher;
    private final Clock clock;
    private final int threshold;
    private final int windowDays;

    public RecurringDefectService(DefectRepository defectRepository, RecurringDefectAlertRepository alertRepository,
                                  VehicleRepository vehicleRepository, NotificationService notificationService,
                                  NotificationDispatcher dispatcher, Clock clock,
                                  @Value("${fleetguard.recurring-defects.threshold:3}") int threshold,
                                  @Value("${fleetguard.recurring-defects.window-days:30}") int windowDays) {
        this.defectRepository = defectRepository;
        this.alertRepository = alertRepository;
        this.vehicleRepository = vehicleRepository;
        this.notificationService = notificationService;
        this.dispatcher = dispatcher;
        this.clock = clock;
        this.threshold = threshold;
        this.windowDays = windowDays;
    }

    /** Los controles recurrentes hoy, de toda la flota o de un vehículo. No mira si se avisó (§5). */
    public RecurringDefectsDto list(String vehicleIdParam) {
        Instant from = windowStart(clock.instant());
        List<Defect> defects = vehicleIdParam != null && !vehicleIdParam.isBlank()
                ? Uuids.parse(vehicleIdParam)
                        .map(id -> defectRepository.findReportedSinceByVehicleIdWithInspection(id, from))
                        .orElse(List.of())
                : defectRepository.findReportedSinceWithInspection(from);

        Map<Key, List<Defect>> byControl = defects.stream()
                .collect(Collectors.groupingBy(RecurringDefectService::key));
        List<Map.Entry<Key, List<Defect>>> recurring = byControl.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= threshold)
                .toList();

        Map<UUID, String> plateByVehicleId = vehicleRepository
                .findAllById(recurring.stream().map(entry -> entry.getKey().vehicleId()).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Vehicle::getId, Vehicle::getPlate));

        List<RecurringDefectsDto.Item> items = recurring.stream()
                .map(entry -> toItem(entry.getKey(), entry.getValue(), plateByVehicleId.get(entry.getKey().vehicleId())))
                .sorted(Comparator.comparingInt(RecurringDefectsDto.Item::count).reversed()
                        .thenComparing(RecurringDefectsDto.Item::lastReportedAt, Comparator.reverseOrder()))
                .toList();
        return new RecurringDefectsDto(threshold, windowDays, items);
    }

    /**
     * Después de guardar una inspección con defectos: por cada control que falló y llegó al umbral, avisa
     * si no se avisó ese control de ese vehículo dentro de la ventana (§4). Devuelve cuántos avisos
     * mandó; lo ignora el listener, sirve para los tests.
     */
    public int alertIfRecurring(UUID vehicleId, String plate, List<String> itemIds) {
        Instant now = clock.instant();
        Instant from = windowStart(now);
        Map<String, List<Defect>> byItem = defectRepository.findReportedSinceByVehicleIdWithInspection(vehicleId, from)
                .stream()
                .collect(Collectors.groupingBy(defect -> defect.getInspectionAnswer().getItemId()));

        int sent = 0;
        Set<String> reported = new LinkedHashSet<>(itemIds);
        for (String itemId : reported) {
            List<Defect> failures = byItem.getOrDefault(itemId, List.of());
            if (failures.size() < threshold) {
                continue;
            }
            String label = label(itemId, failures);
            // Primero la marca, en la misma transacción que el aviso (igual que CAM-101): si el aviso
            // falla, se deshace y el próximo defecto de ese control lo vuelve a intentar.
            boolean[] marked = {false};
            boolean committed = dispatcher.dispatch("defecto recurrente " + itemId + " en " + plate, () -> {
                if (alertRepository.existsByVehicleIdAndItemIdAndSentAtAfter(vehicleId, itemId, from)) {
                    return;
                }
                alertRepository.save(new RecurringDefectAlert(vehicleId, itemId, now));
                marked[0] = true;
                notificationService.notifyConfiguredRecipients(NotificationType.RECURRING_DEFECT,
                        "Defecto recurrente en " + plate,
                        label + " de " + plate + ": " + failures.size() + " defectos en " + windowDays + " días.",
                        "/vehiculos/" + vehicleId);
            });
            if (committed && marked[0]) {
                sent++;
            }
        }
        return sent;
    }

    private Instant windowStart(Instant now) {
        return now.minus(Duration.ofDays(windowDays));
    }

    private static RecurringDefectsDto.Item toItem(Key key, List<Defect> defects, String plate) {
        List<Defect> newestFirst = defects.stream()
                .sorted(Comparator.comparing(Defect::getCreatedAt).reversed())
                .toList();
        List<RecurringDefectsDto.Occurrence> occurrences = newestFirst.stream()
                .map(defect -> new RecurringDefectsDto.Occurrence(defect.getId().toString(), defect.getCreatedAt(),
                        defect.getSeverity().toJson(), defect.getStatus(), defect.getDescription()))
                .toList();
        return new RecurringDefectsDto.Item(key.vehicleId().toString(), plate, key.itemId(),
                label(key.itemId(), newestFirst), defects.size(), newestFirst.get(0).getCreatedAt(), occurrences);
    }

    /** El nombre del control en la falla más reciente que lo tenga guardado; si ninguna, el id (§3). */
    private static String label(String itemId, List<Defect> defects) {
        return defects.stream()
                .sorted(Comparator.comparing(Defect::getCreatedAt).reversed())
                .map(Defect::getInspectionAnswer)
                .map(InspectionAnswer::getItemLabel)
                .filter(itemLabel -> itemLabel != null && !itemLabel.isBlank())
                .findFirst()
                .orElse(itemId);
    }

    private static Key key(Defect defect) {
        InspectionAnswer answer = defect.getInspectionAnswer();
        return new Key(answer.getInspection().getVehicleId(), answer.getItemId());
    }

    private record Key(UUID vehicleId, String itemId) {
    }
}
