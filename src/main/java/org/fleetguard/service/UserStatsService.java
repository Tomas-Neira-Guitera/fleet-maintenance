package org.fleetguard.service;

import org.fleetguard.dto.DriverStatsDto;
import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.dto.TechnicianStatsDto;
import org.fleetguard.dto.UserStatsDto;
import org.fleetguard.entity.DefectSeverity;
import org.fleetguard.entity.Role;
import org.fleetguard.entity.User;
import org.fleetguard.entity.WorkOrder;
import org.fleetguard.entity.WorkOrderStatus;
import org.fleetguard.exception.UserValidationException;
import org.fleetguard.repository.DefectRepository;
import org.fleetguard.repository.DriverCount;
import org.fleetguard.repository.InspectionRepository;
import org.fleetguard.repository.TripRepository;
import org.fleetguard.repository.UserAmount;
import org.fleetguard.repository.UserCount;
import org.fleetguard.repository.UserRepository;
import org.fleetguard.repository.WorkOrderExpenseRepository;
import org.fleetguard.repository.WorkOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Actividad de choferes y técnicos en un período -- GET /api/users/stats (CAM-91). Todo se
 * cuenta por id de usuario, así que lo anterior a CAM-85 (sin chofer real) no suma a nadie.
 */
@Service
public class UserStatsService {

    private static final List<WorkOrderStatus> OPEN_STATUSES = List.of(WorkOrderStatus.ASIGNADA, WorkOrderStatus.EN_PROCESO);

    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final InspectionRepository inspectionRepository;
    private final DefectRepository defectRepository;
    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderExpenseRepository expenseRepository;

    public UserStatsService(UserRepository userRepository, TripRepository tripRepository,
                            InspectionRepository inspectionRepository, DefectRepository defectRepository,
                            WorkOrderRepository workOrderRepository, WorkOrderExpenseRepository expenseRepository) {
        this.userRepository = userRepository;
        this.tripRepository = tripRepository;
        this.inspectionRepository = inspectionRepository;
        this.defectRepository = defectRepository;
        this.workOrderRepository = workOrderRepository;
        this.expenseRepository = expenseRepository;
    }

    @Transactional(readOnly = true)
    public UserStatsDto stats(String from, String to) {
        List<FieldValidationErrorDetail> details = new ArrayList<>();
        DateRange range = DateRange.parse(from, to, details);
        if (!details.isEmpty()) {
            throw new UserValidationException("Período inválido", details);
        }
        return new UserStatsDto(driverStats(range), technicianStats(range));
    }

    private List<DriverStatsDto> driverStats(DateRange range) {
        Map<UUID, Long> trips = tripRepository.countTripsByDriver(range.from(), range.to()).stream()
                .collect(Collectors.toMap(UserCount::userId, UserCount::count));
        Map<String, Long> inspections = byDriver(inspectionRepository.countByDriver(range.from(), range.to()));
        Map<String, Long> blocking = byDriver(
                defectRepository.countByDriver(DefectSeverity.BLOCKING, range.from(), range.to()));
        Map<String, Long> nonBlocking = byDriver(
                defectRepository.countByDriver(DefectSeverity.NON_BLOCKING, range.from(), range.to()));

        return userRepository.findByRoleOrderByUsernameAsc(Role.CHOFER).stream()
                .map(user -> {
                    String id = user.getId().toString();
                    long blockingCount = blocking.getOrDefault(id, 0L);
                    return new DriverStatsDto(id, user.getUsername(), user.isActive(),
                            trips.getOrDefault(user.getId(), 0L), inspections.getOrDefault(id, 0L),
                            blockingCount + nonBlocking.getOrDefault(id, 0L), blockingCount);
                })
                .toList();
    }

    private List<TechnicianStatsDto> technicianStats(DateRange range) {
        Map<UUID, Long> open = workOrderRepository.countByTechnician(OPEN_STATUSES).stream()
                .collect(Collectors.toMap(UserCount::userId, UserCount::count));
        Map<UUID, List<WorkOrder>> finalized = workOrderRepository
                .findFinalizedWithTechnician(range.from(), range.to()).stream()
                .collect(Collectors.groupingBy(WorkOrder::getTechnicianId));
        Map<UUID, BigDecimal> expenses = expenseRepository.sumByTechnician(range.from(), range.to()).stream()
                .collect(Collectors.toMap(UserAmount::userId, UserAmount::amount));

        return userRepository.findByRoleOrderByUsernameAsc(Role.TECNICO).stream()
                .map(user -> toTechnicianStats(user, open.getOrDefault(user.getId(), 0L),
                        finalized.getOrDefault(user.getId(), List.of()),
                        expenses.getOrDefault(user.getId(), BigDecimal.ZERO)))
                .toList();
    }

    private static TechnicianStatsDto toTechnicianStats(User user, long open, List<WorkOrder> finalized, BigDecimal expenses) {
        // Con un decimal alcanza: es un promedio para comparar técnicos, no una medición.
        Double avgHours = finalized.isEmpty() ? null : Math.round(finalized.stream()
                .mapToDouble(w -> Duration.between(w.getCreatedAt(), w.getFinalizedAt()).toMinutes() / 60.0)
                .average()
                .orElse(0) * 10) / 10.0;
        return new TechnicianStatsDto(user.getId().toString(), user.getUsername(), user.isActive(), open,
                finalized.size(), avgHours, expenses);
    }

    private static Map<String, Long> byDriver(List<DriverCount> counts) {
        return counts.stream().collect(Collectors.toMap(DriverCount::driverId, DriverCount::count));
    }
}
