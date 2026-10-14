package org.fleetguard.service;

import org.fleetguard.dto.VehicleCostsDto;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.WorkOrderExpense;
import org.fleetguard.exception.VehicleNotFoundException;
import org.fleetguard.repository.InspectionRepository;
import org.fleetguard.repository.MaintenanceCompletionRepository;
import org.fleetguard.repository.VehicleRepository;
import org.fleetguard.repository.WorkOrderExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/** Lógica de GET /api/vehicles/{id}/costs -- ver openapi.yaml y CAM-105-vehicle-costs-contract.md. */
@Service
public class VehicleCostsService {

    private static final ZoneId FLEET_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    private final VehicleRepository vehicleRepository;
    private final WorkOrderExpenseRepository expenseRepository;
    private final InspectionRepository inspectionRepository;
    private final MaintenanceCompletionRepository completionRepository;

    public VehicleCostsService(VehicleRepository vehicleRepository, WorkOrderExpenseRepository expenseRepository,
                               InspectionRepository inspectionRepository,
                               MaintenanceCompletionRepository completionRepository) {
        this.vehicleRepository = vehicleRepository;
        this.expenseRepository = expenseRepository;
        this.inspectionRepository = inspectionRepository;
        this.completionRepository = completionRepository;
    }

    @Transactional(readOnly = true)
    public VehicleCostsDto getCosts(String id) {
        return getCosts(id, Instant.now());
    }

    /** Con "ahora" explícito, para poder probar el corte de los últimos 12 meses. */
    VehicleCostsDto getCosts(String id, Instant now) {
        UUID vehicleId = Uuids.parse(id).orElseThrow(() -> new VehicleNotFoundException(id));
        Vehicle vehicle = vehicleRepository.findById(vehicleId).orElseThrow(() -> new VehicleNotFoundException(id));

        List<WorkOrderExpense> expenses = expenseRepository.findByVehicleId(vehicleId);
        // Desde la medianoche argentina del mismo día de hace un año: la misma ventana con la que
        // "Ver gastos" abre la pestaña Gastos, así las dos cifras coinciden.
        Instant yearAgo = now.atZone(FLEET_ZONE).toLocalDate().minusYears(1).atStartOfDay(FLEET_ZONE).toInstant();
        BigDecimal total = sum(expenses.stream());
        BigDecimal last12Months = sum(expenses.stream().filter(e -> !e.getCreatedAt().isBefore(yearAgo)));

        Long firstKnownKm = firstKnownKm(vehicleId);
        long currentKm = vehicle.getOdometerKm();
        // Sin un registro anterior con menos km que el actual no hay recorrido para dividir.
        Long kmDriven = firstKnownKm != null && currentKm > firstKnownKm ? currentKm - firstKnownKm : null;
        BigDecimal costPerKm = kmDriven != null
                ? total.divide(BigDecimal.valueOf(kmDriven), 2, RoundingMode.HALF_UP)
                : null;

        return new VehicleCostsDto(total, last12Months, expenses.size(), currentKm, firstKnownKm, kmDriven, costPerKm);
    }

    /** Un km registrado y cuándo: un mantenimiento hecho solo guarda el día, que cuenta desde su inicio (UTC). */
    private record Reading(Instant at, long km) {
    }

    /**
     * El km del registro más antiguo: inspecciones y mantenimientos hechos son lo único que guarda
     * el km con que se hicieron. El más antiguo y no el menor: una inspección de hoy con un km mal
     * tipeado (menor al real, que la inspección no rechaza) no tiene que mover el punto de partida.
     * Si dos registros caen en el mismo momento, gana el menor km.
     */
    private Long firstKnownKm(UUID vehicleId) {
        Stream<Reading> fromInspections = inspectionRepository.findByVehicleIdOrderByTimestampDesc(vehicleId).stream()
                .filter(inspection -> inspection.getOdometerKm() != null && inspection.getTimestamp() != null)
                .map(inspection -> new Reading(inspection.getTimestamp(), (long) Math.floor(inspection.getOdometerKm())));
        Stream<Reading> fromCompletions = completionRepository.findByVehicleIdWithPlan(vehicleId).stream()
                .filter(completion -> completion.getCompletedKm() != null)
                .map(completion -> new Reading(completion.getCompletedAt().atStartOfDay(ZoneOffset.UTC).toInstant(),
                        completion.getCompletedKm()));
        return Stream.concat(fromInspections, fromCompletions)
                .min(Comparator.comparing(Reading::at).thenComparingLong(Reading::km))
                .map(Reading::km)
                .orElse(null);
    }

    private static BigDecimal sum(Stream<WorkOrderExpense> expenses) {
        return expenses.map(WorkOrderExpense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
