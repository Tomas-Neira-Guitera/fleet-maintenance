package org.example.service;

import org.example.dto.CreateVehicleRequest;
import org.example.dto.FieldValidationErrorDetail;
import org.example.dto.FleetStatusRowDto;
import org.example.dto.NextMaintenanceDto;
import org.example.dto.OdometerResultDto;
import org.example.dto.PagedResponse;
import org.example.dto.UpdateVehicleRequest;
import org.example.dto.VehicleSummaryDto;
import org.example.entity.MaintenanceStatus;
import org.example.entity.TripStatus;
import org.example.entity.Vehicle;
import org.example.entity.VehicleMaintenanceAssignment;
import org.example.exception.MaintenanceConflictException;
import org.example.exception.VehicleNotFoundException;
import org.example.exception.VehicleStateConflictException;
import org.example.exception.VehicleValidationException;
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

    private static final int MIN_YEAR = 1980;

    public VehicleService(VehicleRepository vehicleRepository, TripRepository tripRepository,
                           VehicleMaintenanceAssignmentRepository assignmentRepository, VehicleMapper vehicleMapper) {
        this.vehicleRepository = vehicleRepository;
        this.tripRepository = tripRepository;
        this.assignmentRepository = assignmentRepository;
        this.vehicleMapper = vehicleMapper;
    }

    public List<VehicleSummaryDto> listVehicles(boolean active) {
        return vehicleRepository.findByActive(active).stream()
                .map(vehicle -> vehicleMapper.toSummary(vehicle, hasOpenTrip(vehicle.getId())))
                .toList();
    }

    /** GET /api/vehicles/{id} -- CAM-22. Incluye vehículos dados de baja. */
    public VehicleSummaryDto getById(String id) {
        UUID vehicleId;
        try {
            vehicleId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new VehicleNotFoundException(id);
        }
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new VehicleNotFoundException(id));
        return vehicleMapper.toSummary(vehicle, hasOpenTrip(vehicle.getId()));
    }

    private boolean hasOpenTrip(UUID vehicleId) {
        return tripRepository.findFirstByVehicle_IdAndStatus(vehicleId, TripStatus.OPEN).isPresent();
    }

    /** GET /api/vehicles?view=fleet-status -- CAM-40. */
    public PagedResponse<FleetStatusRowDto> getFleetStatus(int page, int pageSize, String statusFilter, boolean active) {
        List<Vehicle> vehicles = vehicleRepository.findByActive(active);
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
        Vehicle vehicle = find(vehicleId);
        if (!vehicle.isActive()) {
            throw new VehicleStateConflictException("VEHICLE_INACTIVE",
                    "El vehículo está dado de baja -- reactivalo primero con PATCH { \"active\": true }");
        }
        if (odometerKm < vehicle.getOdometerKm()) {
            throw new MaintenanceConflictException("ODOMETER_REGRESSION",
                    "El kilometraje no puede ser menor al ya cargado (" + vehicle.getOdometerKm() + " km)");
        }
        vehicle.setOdometerKm(odometerKm);
        vehicleRepository.save(vehicle);
        return new OdometerResultDto(vehicle.getId().toString(), vehicle.getOdometerKm(), Instant.now().toString());
    }

    /** POST /api/vehicles -- CAM-25. */
    @Transactional
    public VehicleSummaryDto create(CreateVehicleRequest request) {
        List<FieldValidationErrorDetail> details = new ArrayList<>();
        if (request.plate() == null || request.plate().isBlank()) {
            details.add(new FieldValidationErrorDetail("plate", "La patente es obligatoria"));
        }
        if (request.brand() == null || request.brand().isBlank()) {
            details.add(new FieldValidationErrorDetail("brand", "La marca es obligatoria"));
        }
        if (request.model() == null || request.model().isBlank()) {
            details.add(new FieldValidationErrorDetail("model", "El modelo es obligatorio"));
        }
        validateYear(request.year(), details);
        if (!details.isEmpty()) {
            throw new VehicleValidationException("Datos inválidos para crear el vehículo", details);
        }

        if (vehicleRepository.existsByPlateIgnoreCase(request.plate())) {
            throw new VehicleStateConflictException("DUPLICATE_PLATE",
                    "Ya existe un vehículo con la patente " + request.plate());
        }

        Vehicle vehicle = new Vehicle(request.plate(), request.brand(), request.model());
        vehicle.setVehicleType(request.vehicleType());
        vehicle.setYear(request.year());
        vehicle.setChassisNumber(request.chassisNumber());
        if (request.odometerKm() != null) {
            vehicle.setOdometerKm(request.odometerKm());
        }
        vehicle = vehicleRepository.save(vehicle);
        return vehicleMapper.toSummary(vehicle, false);
    }

    /** PATCH /api/vehicles/{id} -- CAM-25. Campos ausentes no se modifican. */
    @Transactional
    public VehicleSummaryDto update(String id, UpdateVehicleRequest request) {
        Vehicle vehicle = find(id);

        boolean onlyReactivating = Boolean.TRUE.equals(request.active())
                && request.plate() == null && request.brand() == null && request.model() == null
                && request.vehicleType() == null && request.year() == null && request.chassisNumber() == null;
        if (!vehicle.isActive() && !onlyReactivating) {
            throw new VehicleStateConflictException("VEHICLE_INACTIVE",
                    "El vehículo está dado de baja -- reactivalo primero con PATCH { \"active\": true }");
        }

        if (request.plate() != null) {
            if (vehicleRepository.existsByPlateIgnoreCaseAndIdNot(request.plate(), vehicle.getId())) {
                throw new VehicleStateConflictException("DUPLICATE_PLATE",
                        "Ya existe un vehículo con la patente " + request.plate());
            }
            vehicle.setPlate(request.plate());
        }

        List<FieldValidationErrorDetail> details = new ArrayList<>();
        if (request.brand() != null && request.brand().isBlank()) {
            details.add(new FieldValidationErrorDetail("brand", "La marca no puede quedar vacía"));
        }
        if (request.model() != null && request.model().isBlank()) {
            details.add(new FieldValidationErrorDetail("model", "El modelo no puede quedar vacío"));
        }
        validateYear(request.year(), details);
        if (!details.isEmpty()) {
            throw new VehicleValidationException("Datos inválidos para editar el vehículo", details);
        }

        if (request.brand() != null) {
            vehicle.setBrand(request.brand());
        }
        if (request.model() != null) {
            vehicle.setModel(request.model());
        }
        if (request.vehicleType() != null) {
            vehicle.setVehicleType(request.vehicleType());
        }
        if (request.year() != null) {
            vehicle.setYear(request.year());
        }
        if (request.chassisNumber() != null) {
            vehicle.setChassisNumber(request.chassisNumber());
        }
        if (request.active() != null) {
            vehicle.setActive(request.active());
        }

        vehicle = vehicleRepository.save(vehicle);
        return vehicleMapper.toSummary(vehicle, hasOpenTrip(vehicle.getId()));
    }

    /** DELETE /api/vehicles/{id} -- baja lógica (CAM-25), mismo patrón que las asignaciones de mantenimiento. */
    @Transactional
    public void deactivate(String id) {
        Vehicle vehicle = find(id);
        if (hasOpenTrip(vehicle.getId())) {
            throw new VehicleStateConflictException("VEHICLE_ON_TRIP",
                    "El vehículo tiene un viaje abierto -- no se puede dar de baja");
        }
        vehicle.setActive(false);
        vehicleRepository.save(vehicle);
    }

    private Vehicle find(String id) {
        return vehicleRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new VehicleNotFoundException(id));
    }

    private void validateYear(Integer year, List<FieldValidationErrorDetail> details) {
        if (year == null) {
            return;
        }
        int currentYear = LocalDate.now().getYear();
        if (year < MIN_YEAR || year > currentYear + 1) {
            details.add(new FieldValidationErrorDetail("year",
                    "Debe estar entre " + MIN_YEAR + " y " + (currentYear + 1)));
        }
    }
}
