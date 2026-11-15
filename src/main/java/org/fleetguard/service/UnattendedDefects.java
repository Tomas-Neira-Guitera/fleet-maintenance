package org.fleetguard.service;

import org.fleetguard.entity.Defect;
import org.fleetguard.entity.ScheduleStatus;
import org.fleetguard.entity.WorkOrderStatus;
import org.fleetguard.repository.ScheduledMaintenanceRepository;
import org.fleetguard.repository.WorkOrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * CAM-142: qué defectos están sin atender. Un defecto lo está si sigue abierto, lleva más de N días desde que
 * se reportó (en días de Argentina) y no tiene una OT abierta (asignada, en proceso o en espera) ni un turno
 * programado. Una OT o un turno cancelados no cuentan. Lo usan GET /api/defects y la tarea diaria del aviso.
 */
@Component
public class UnattendedDefects {

    static final ZoneId FLEET_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    private final WorkOrderRepository workOrderRepository;
    private final ScheduledMaintenanceRepository scheduleRepository;
    private final Clock clock;
    private final int days;

    public UnattendedDefects(WorkOrderRepository workOrderRepository, ScheduledMaintenanceRepository scheduleRepository,
                             Clock clock, @Value("${fleetguard.unattended-defects.days:7}") int days) {
        this.workOrderRepository = workOrderRepository;
        this.scheduleRepository = scheduleRepository;
        this.clock = clock;
        this.days = days;
    }

    public int days() {
        return days;
    }

    /**
     * Los días desde que se reportó cada defecto sin atender, por id. Los atendidos no están. Dos consultas en
     * total, sin importar cuántos defectos sean.
     */
    public Map<UUID, Long> unattendedDays(Collection<Defect> defects) {
        LocalDate today = LocalDate.now(clock.withZone(FLEET_ZONE));
        Map<UUID, Long> candidates = new HashMap<>();
        for (Defect defect : defects) {
            if (!"open".equals(defect.getStatus())) {
                continue;
            }
            long age = ChronoUnit.DAYS.between(defect.getCreatedAt().atZone(FLEET_ZONE).toLocalDate(), today);
            if (age > days) {
                candidates.put(defect.getId(), age);
            }
        }
        if (candidates.isEmpty()) {
            return candidates;
        }
        Set<UUID> attended = new HashSet<>(workOrderRepository.findDefectIdsWithStatusIn(WorkOrderStatus.OPEN));
        attended.addAll(scheduleRepository.findDefectIdsWithStatus(ScheduleStatus.SCHEDULED));
        candidates.keySet().removeAll(attended);
        return candidates;
    }
}
