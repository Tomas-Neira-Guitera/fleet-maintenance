package org.fleetguard.service;

import org.fleetguard.entity.MaintenanceAlertLog;
import org.fleetguard.entity.MaintenanceAlertStage;
import org.fleetguard.entity.MaintenancePlan;
import org.fleetguard.entity.NotificationChannel;
import org.fleetguard.entity.NotificationType;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.VehicleMaintenanceAssignment;
import org.fleetguard.repository.MaintenanceAlertLogRepository;
import org.fleetguard.repository.VehicleMaintenanceAssignmentRepository;
import org.fleetguard.repository.VehicleRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Avisos de vencimiento de planes (CAM-118): uno al entrar en la anticipación y otro al vencer, una vez
 * por ciclo. Por fecha se revisa una vez por día; por km, cada vez que sube el km (MaintenanceAlertListener).
 */
@Service
public class MaintenanceAlertService {

    static final ZoneId FLEET_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    private final VehicleMaintenanceAssignmentRepository assignmentRepository;
    private final VehicleRepository vehicleRepository;
    private final MaintenanceAlertLogRepository alertLogRepository;
    private final PlanNotificationSettingsService planSettings;
    private final NotificationSettingsService settings;
    private final NotificationService notificationService;
    private final NotificationDispatcher dispatcher;
    private final LinkedDocumentDueDates linkedDueDates;
    private final Clock clock;

    public MaintenanceAlertService(VehicleMaintenanceAssignmentRepository assignmentRepository,
                                   VehicleRepository vehicleRepository, MaintenanceAlertLogRepository alertLogRepository,
                                   PlanNotificationSettingsService planSettings, NotificationSettingsService settings,
                                   NotificationService notificationService, NotificationDispatcher dispatcher,
                                   LinkedDocumentDueDates linkedDueDates, Clock clock) {
        this.assignmentRepository = assignmentRepository;
        this.vehicleRepository = vehicleRepository;
        this.alertLogRepository = alertLogRepository;
        this.planSettings = planSettings;
        this.settings = settings;
        this.notificationService = notificationService;
        this.dispatcher = dispatcher;
        this.linkedDueDates = linkedDueDates;
        this.clock = clock;
    }

    /** Devuelve cuántos avisos mandó; el valor lo ignora el scheduler, sirve para los tests. */
    @Scheduled(cron = "${fleetguard.maintenance-alerts.cron:0 0 8 * * *}", zone = "America/Argentina/Buenos_Aires")
    public int checkAll() {
        return check(assignmentRepository.findByActiveTrue());
    }

    public int checkVehicle(UUID vehicleId) {
        return check(assignmentRepository.findByVehicleIdAndActive(vehicleId, true));
    }

    private int check(List<VehicleMaintenanceAssignment> assignments) {
        if (assignments.isEmpty()) {
            return 0;
        }
        LocalDate today = LocalDate.now(clock.withZone(FLEET_ZONE));
        List<UUID> vehicleIds = assignments.stream().map(VehicleMaintenanceAssignment::getVehicleId).distinct().toList();
        Map<UUID, Vehicle> vehicles = vehicleRepository.findAllById(vehicleIds).stream()
                .filter(Vehicle::isActive)
                .collect(Collectors.toMap(Vehicle::getId, Function.identity()));
        EffectiveNotificationSetting dueSoon = settings.effective(NotificationType.MAINTENANCE_DUE_SOON);
        EffectiveNotificationSetting overdue = settings.effective(NotificationType.MAINTENANCE_OVERDUE);
        Map<UUID, PlanNotificationSettingsService.AlertConfig> plans = new HashMap<>();

        int sent = 0;
        for (VehicleMaintenanceAssignment assignment : assignments) {
            MaintenancePlan plan = assignment.getMaintenancePlan();
            Vehicle vehicle = vehicles.get(assignment.getVehicleId());
            if (vehicle == null || !plan.isActive()) {
                continue;
            }
            // CAM-127: si vence con un documento cargado, avisa el documento (DocumentAlertService), no el plan.
            if (linkedDueDates.documentDueDate(assignment).isPresent()) {
                continue;
            }
            PlanNotificationSettingsService.AlertConfig config = plans.computeIfAbsent(plan.getId(), planSettings::alertConfig);
            MaintenanceAlertStage stage = MaintenanceAlertRules.stage(assignment.getNextDueKm(), assignment.getNextDueDate(),
                    vehicle.getOdometerKm(), today,
                    config.custom() ? config.leadKm() : dueSoon.leadKm(),
                    config.custom() ? config.leadDays() : dueSoon.leadDays());
            if (stage == null) {
                continue;
            }
            boolean enabled = config.custom() ? config.enabled()
                    : (stage == MaintenanceAlertStage.DUE_SOON ? dueSoon : overdue).enabled();
            if (enabled && send(assignment, plan, vehicle, stage, config.custom() ? config.channels() : null, today)) {
                sent++;
            }
        }
        return sent;
    }

    private boolean send(VehicleMaintenanceAssignment assignment, MaintenancePlan plan, Vehicle vehicle,
                         MaintenanceAlertStage stage, Set<NotificationChannel> channels, LocalDate today) {
        String cycle = MaintenanceAlertRules.cycleKey(assignment.getNextDueKm(), assignment.getNextDueDate());
        if (alertLogRepository.existsByAssignmentIdAndStageAndCycleKey(assignment.getId(), stage, cycle)) {
            return false;
        }
        boolean dueSoon = stage == MaintenanceAlertStage.DUE_SOON;
        NotificationType type = dueSoon ? NotificationType.MAINTENANCE_DUE_SOON : NotificationType.MAINTENANCE_OVERDUE;
        String title = dueSoon ? "Mantenimiento por vencer" : "Mantenimiento vencido";
        String message = MaintenanceAlertRules.message(stage, plan.getName(), vehicle.getPlate(), assignment.getNextDueKm(),
                assignment.getNextDueDate(), vehicle.getOdometerKm(), today);
        // La marca y el aviso van en la misma transacción: si el aviso falla, se reintenta en la próxima revisión.
        boolean[] marked = {false};
        boolean committed = dispatcher.dispatch(type.toJson() + " de la asignación " + assignment.getId(), () -> {
            if (alertLogRepository.existsByAssignmentIdAndStageAndCycleKey(assignment.getId(), stage, cycle)) {
                return;
            }
            alertLogRepository.saveAndFlush(new MaintenanceAlertLog(assignment.getId(), stage, cycle, clock.instant()));
            marked[0] = true;
            notificationService.notifyConfiguredRecipients(type, title, message, "/vehiculos/" + vehicle.getId(), channels);
        });
        return committed && marked[0];
    }
}
