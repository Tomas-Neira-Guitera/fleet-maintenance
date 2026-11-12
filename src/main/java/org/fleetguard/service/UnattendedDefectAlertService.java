package org.fleetguard.service;

import org.fleetguard.entity.Defect;
import org.fleetguard.entity.NotificationType;
import org.fleetguard.entity.UnattendedDefectAlert;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.repository.DefectRepository;
import org.fleetguard.repository.UnattendedDefectAlertRepository;
import org.fleetguard.repository.VehicleRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Aviso de defectos sin atender (CAM-142): una vez por día, un aviso por vehículo con los defectos que
 * pasaron a estar sin atender (ver UnattendedDefects) y todavía no se avisaron. Cada defecto se avisa una
 * sola vez (unattended_defect_alerts). Lleva al detalle del vehículo.
 */
@Service
public class UnattendedDefectAlertService {

    private final DefectRepository defectRepository;
    private final VehicleRepository vehicleRepository;
    private final UnattendedDefectAlertRepository alertRepository;
    private final UnattendedDefects unattendedDefects;
    private final NotificationSettingsService settings;
    private final NotificationService notificationService;
    private final NotificationDispatcher dispatcher;
    private final Clock clock;

    public UnattendedDefectAlertService(DefectRepository defectRepository, VehicleRepository vehicleRepository,
                                        UnattendedDefectAlertRepository alertRepository,
                                        UnattendedDefects unattendedDefects, NotificationSettingsService settings,
                                        NotificationService notificationService, NotificationDispatcher dispatcher,
                                        Clock clock) {
        this.defectRepository = defectRepository;
        this.vehicleRepository = vehicleRepository;
        this.alertRepository = alertRepository;
        this.unattendedDefects = unattendedDefects;
        this.settings = settings;
        this.notificationService = notificationService;
        this.dispatcher = dispatcher;
        this.clock = clock;
    }

    /**
     * La corrida diaria. Con el aviso apagado no se anota nada, así que al prenderlo se avisa lo pendiente.
     * Devuelve cuántos avisos mandó, para los tests.
     */
    @Scheduled(cron = "${fleetguard.unattended-defects.cron:0 10 8 * * *}", zone = "America/Argentina/Buenos_Aires")
    public int checkAll() {
        if (!settings.effective(NotificationType.DEFECT_UNATTENDED).enabled()) {
            return 0;
        }
        List<Defect> open = defectRepository.findAllWithInspection().stream()
                .filter(d -> "open".equals(d.getStatus())).toList();
        Map<UUID, Long> unattended = unattendedDefects.unattendedDays(open);
        if (unattended.isEmpty()) {
            return 0;
        }
        Set<UUID> alerted = new HashSet<>(alertRepository.findAlertedDefectIds(unattended.keySet()));

        // Por vehículo, del más viejo al más nuevo.
        Map<UUID, List<Defect>> byVehicle = open.stream()
                .filter(d -> unattended.containsKey(d.getId()) && !alerted.contains(d.getId()))
                .sorted(Comparator.comparing(Defect::getCreatedAt))
                .collect(Collectors.groupingBy(UnattendedDefectAlertService::vehicleId, LinkedHashMap::new,
                        Collectors.toList()));
        if (byVehicle.isEmpty()) {
            return 0;
        }
        Map<UUID, Vehicle> vehicles = vehicleRepository.findAllById(byVehicle.keySet()).stream()
                .filter(Vehicle::isActive)
                .collect(Collectors.toMap(Vehicle::getId, Function.identity()));

        int sent = 0;
        for (Map.Entry<UUID, List<Defect>> entry : byVehicle.entrySet()) {
            Vehicle vehicle = vehicles.get(entry.getKey());
            if (vehicle != null && send(vehicle, entry.getValue(), unattended)) {
                sent++;
            }
        }
        return sent;
    }

    private boolean send(Vehicle vehicle, List<Defect> defects, Map<UUID, Long> unattended) {
        List<UUID> ids = defects.stream().map(Defect::getId).toList();
        String title = title(vehicle.getPlate(), defects.size());
        String message = message(defects, unattended, unattendedDefects.days());
        // La marca y el aviso van juntos: si el aviso falla, se reintenta en la próxima corrida.
        boolean[] marked = {false};
        boolean committed = dispatcher.dispatch("defectos sin atender de " + vehicle.getPlate(), () -> {
            if (!alertRepository.findAlertedDefectIds(ids).isEmpty()) {
                return;
            }
            alertRepository.saveAllAndFlush(ids.stream().map(id -> new UnattendedDefectAlert(id, clock.instant())).toList());
            marked[0] = true;
            notificationService.notifyConfiguredRecipients(NotificationType.DEFECT_UNATTENDED, title, message,
                    "/vehiculos/" + vehicle.getId());
        });
        return committed && marked[0];
    }

    /** "Defecto sin atender en AB123CD" o "2 defectos sin atender en AB123CD". */
    static String title(String plate, int count) {
        return count == 1 ? "Defecto sin atender en " + plate : count + " defectos sin atender en " + plate;
    }

    /**
     * "«Luz de freno» lleva 9 días sin OT ni turno." o
     * "3 defectos llevan más de 7 días sin OT ni turno: Luz de freno (2 veces); Espejo roto." Los del mismo
     * nombre (el mismo control reportado en varias inspecciones) se juntan.
     */
    static String message(List<Defect> defects, Map<UUID, Long> unattended, int days) {
        if (defects.size() == 1) {
            Defect defect = defects.get(0);
            return "«" + defect.getDescription() + "» lleva " + unattended.get(defect.getId()) + " días sin OT ni turno.";
        }
        Map<String, Long> byDescription = defects.stream()
                .collect(Collectors.groupingBy(Defect::getDescription, LinkedHashMap::new, Collectors.counting()));
        return defects.size() + " defectos llevan más de " + days + " días sin OT ni turno: "
                + byDescription.entrySet().stream()
                .map(e -> e.getValue() == 1 ? e.getKey() : e.getKey() + " (" + e.getValue() + " veces)")
                .collect(Collectors.joining("; ")) + ".";
    }

    private static UUID vehicleId(Defect defect) {
        return defect.getInspectionAnswer().getInspection().getVehicleId();
    }
}
