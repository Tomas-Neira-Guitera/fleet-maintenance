package org.example.service;

import org.example.dto.FleetStatusRowDto;
import org.example.dto.NextMaintenanceDto;
import org.example.dto.OdometerResultDto;
import org.example.dto.PagedResponse;
import org.example.dto.VehicleSummaryDto;
import org.example.entity.MaintenanceStatus;
import org.example.entity.TripStatus;
import org.example.entity.Vehicle;
import org.example.entity.VehicleMaintenanceAssignment;
import org.example.exception.MaintenanceConflictException;
import org.example.exception.VehicleNotFoundException;
import org.example.mapper.VehicleMapper;
import org.example.repository.TripRepository;
import org.example.repository.VehicleMaintenanceAssignmentRepository;
import org.example.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Lógica de negocio de /api/vehicles -- ver openapi.yaml y CAM-40-maintenance-api-contract.md. */
@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final TripRepository tripRepository;
    private final VehicleMaintenanceAssignmentRepository assignmentRepository;
    private final VehicleMapper vehicleMapper;

    public VehicleService(VehicleRepository vehicleRepository, TripRepository tripRepository,
                           VehicleMaintenanceAssignmentRepository assignmentRepository, VehicleMapper vehicleMapper) {
        this.vehicleRepository = vehicleRepository;
        this.tripRepository = tripRepository;
        this.assignmentRepository = assignmentRepository;
        this.vehicleMapper = vehicleMapper;
    }

    public List<VehicleSummaryDto> listVehicles() {
        return vehicleRepository.findAll().stream()
                .map(vehicle -> vehicleMapper.toSummary(vehicle, hasOpenTrip(vehicle.getId())))
                .toList();
    }

    private boolean hasOpenTrip(UUID vehicleId) {
        return tripRepository.findFirstByVehicle_IdAndStatus(vehicleId, TripStatus.OPEN).isPresent();
    }

    /** GET /api/vehicles?view=fleet-status -- CAM-40. */
    public PagedResponse<FleetStatusRowDto> getFleetStatus(int page, int pageSize, String statusFilter) {
        List<Vehicle> vehicles = vehicleRepository.findAll();
        Map<UUID, List<VehicleMaintenanceAssignment>> byVehicle = assignmentRepository
                .findByVehicleIdIn(vehicles.stream().map(Vehicle::getId).toList())
                .stream()
                .filter(VehicleMaintenanceAssignment::isActive)
                .collect(Collectors.groupingBy(VehicleMaintenanceAssignment::getVehicleId));

        LocalDate today = LocalDate.now();
        List<FleetStatusRowDto> rows = new ArrayList<>();
        for (Vehicle vehicle : vehicles) {
            List<VehicleMaintenanceAssignment> assignments = byVehicle.getOrDefault(vehicle.getId(), List.of());
            rows.add(buildRow(vehicle, assignments, today));
        }

        if (statusFilter != null) {
            rows = rows.stream().filter(r -> r.status().equals(statusFilter)).toList();
        }
        rows = rows.stream()
                .sorted(Comparator.comparingInt((FleetStatusRowDto r) -> severityOf(r.status())).reversed())
                .toList();

        long total = rows.size();
        int fromIndex = Math.min((page - 1) * pageSize, rows.size());
        int toIndex = Math.min(fromIndex + pageSize, rows.size());
        return new PagedResponse<>(page, pageSize, total, rows.subList(fromIndex, toIndex));
    }

    private FleetStatusRowDto buildRow(Vehicle vehicle, List<VehicleMaintenanceAssignment> assignments, LocalDate today) {
        long currentKm = vehicle.getOdometerKm();

        List<MaintenanceStatus> statuses = assignments.stream()
                .map(a -> MaintenanceStatusCalculator.computeStatus(a.getNextDueKm(), a.getNextDueDate(), currentKm, today))
                .toList();
        int healthScore = MaintenanceStatusCalculator.healthScore(statuses);

        MaintenanceStatus vehicleStatus = statuses.stream()
                .max(Comparator.comparingInt(MaintenanceStatus::severity))
                .orElse(MaintenanceStatus.AL_DIA);

        NextMaintenanceDto nextMaintenance = pickMostUrgent(assignments, currentKm, today);

        return new FleetStatusRowDto(
                vehicle.getId().toString(),
                vehicle.getPlate(),
                vehicle.getBrand(),
                vehicle.getModel(),
                vehicle.getVehicleType(),
                currentKm,
                healthScore,
                vehicleStatus.toJson(),
                nextMaintenance
        );
    }

    private NextMaintenanceDto pickMostUrgent(List<VehicleMaintenanceAssignment> assignments, long currentKm, LocalDate today) {
        VehicleMaintenanceAssignment best = null;
        MaintenanceStatus bestStatus = null;
        double bestUrgency = -1;

        for (VehicleMaintenanceAssignment a : assignments) {
            MaintenanceStatus status = MaintenanceStatusCalculator.computeStatus(a.getNextDueKm(), a.getNextDueDate(), currentKm, today);
            double urgency = MaintenanceStatusCalculator.urgencyPercent(
                    a.getLastDoneKm(), a.getNextDueKm(), a.getLastDoneDate(), a.getNextDueDate(), currentKm, today);
            if (best == null || status.severity() > bestStatus.severity()
                    || (status.severity() == bestStatus.severity() && urgency > bestUrgency)) {
                best = a;
                bestStatus = status;
                bestUrgency = urgency;
            }
        }

        if (best == null) {
            return null;
        }

        Long remainingKm = best.getNextDueKm() != null ? best.getNextDueKm() - currentKm : null;
        Long remainingDays = best.getNextDueDate() != null
                ? ChronoUnit.DAYS.between(today, best.getNextDueDate())
                : null;

        return new NextMaintenanceDto(
                best.getId().toString(),
                best.getMaintenancePlan().getName(),
                bestStatus.toJson(),
                best.getNextDueDate() == null ? null : best.getNextDueDate().toString(),
                best.getNextDueKm(),
                remainingDays,
                remainingKm
        );
    }

    private int severityOf(String statusJson) {
        for (MaintenanceStatus s : MaintenanceStatus.values()) {
            if (s.toJson().equals(statusJson)) {
                return s.severity();
            }
        }
        return 0;
    }

    /** PATCH /api/vehicles/{id}/odometer -- feature 5.5. */
    @Transactional
    public OdometerResultDto updateOdometer(String vehicleId, long odometerKm) {
        Vehicle vehicle = vehicleRepository.findById(UUID.fromString(vehicleId))
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId));
        if (odometerKm < vehicle.getOdometerKm()) {
            throw new MaintenanceConflictException("ODOMETER_REGRESSION",
                    "El kilometraje no puede ser menor al ya cargado (" + vehicle.getOdometerKm() + " km)");
        }
        vehicle.setOdometerKm(odometerKm);
        vehicleRepository.save(vehicle);
        return new OdometerResultDto(vehicle.getId().toString(), vehicle.getOdometerKm(), Instant.now().toString());
    }
}
