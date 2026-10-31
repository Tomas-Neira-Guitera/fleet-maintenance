package org.fleetguard.service;

import org.fleetguard.dto.ExpenseDto;
import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.dto.VehicleExpenseRequest;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.VehicleExpense;
import org.fleetguard.entity.VehicleExpenseCategory;
import org.fleetguard.entity.WorkOrder;
import org.fleetguard.entity.WorkOrderExpense;
import org.fleetguard.exception.ExpenseNotFoundException;
import org.fleetguard.exception.VehicleNotFoundException;
import org.fleetguard.exception.WorkOrderValidationException;
import org.fleetguard.repository.VehicleExpenseRepository;
import org.fleetguard.repository.VehicleRepository;
import org.fleetguard.repository.WorkOrderExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Lógica de /api/expenses: los gastos que se cargan dentro de cada orden de trabajo (CAM-63), solo
 * lectura acá (CAM-52), y los gastos operativos del vehículo, con su alta, edición y baja (CAM-109).
 * Los totales los calcula el cliente sobre el listado.
 */
@Service
public class ExpenseService {

    private static final ZoneId FLEET_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final String OPERATING_ORIGIN = "operating";
    // Lo que entra en la columna: numeric(12, 2).
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");

    private final WorkOrderExpenseRepository expenseRepository;
    private final VehicleExpenseRepository vehicleExpenseRepository;
    private final VehicleRepository vehicleRepository;

    public ExpenseService(WorkOrderExpenseRepository expenseRepository, VehicleExpenseRepository vehicleExpenseRepository,
                          VehicleRepository vehicleRepository) {
        this.expenseRepository = expenseRepository;
        this.vehicleExpenseRepository = vehicleExpenseRepository;
        this.vehicleRepository = vehicleRepository;
    }

    /** Todos los filtros son opcionales. Un vehicleId, category u origin que no existe da lista vacía, no error. */
    @Transactional(readOnly = true)
    public List<ExpenseDto> list(String vehicleIdParam, String categoryParam, String originParam, String from, String to) {
        List<FieldValidationErrorDetail> details = new ArrayList<>();
        DateRange range = DateRange.parse(from, to, details);
        if (!details.isEmpty()) {
            throw new WorkOrderValidationException("Período inválido", details);
        }

        Stream<ExpenseDto> expenses = Stream.concat(workOrderExpenses(range), operatingExpenses(range));
        if (vehicleIdParam != null && !vehicleIdParam.isBlank()) {
            String vehicleId = Uuids.parse(vehicleIdParam.trim()).map(UUID::toString).orElse(null);
            expenses = expenses.filter(e -> e.vehicleId().equals(vehicleId));
        }
        if (categoryParam != null && !categoryParam.isBlank()) {
            expenses = expenses.filter(e -> e.category().equals(categoryParam));
        }
        if (originParam != null && !originParam.isBlank()) {
            expenses = expenses.filter(e -> e.origin().equals(originParam));
        }
        return expenses.sorted(Comparator.comparing((ExpenseDto e) -> Instant.parse(e.date())).reversed()).toList();
    }

    @Transactional
    public ExpenseDto create(VehicleExpenseRequest request) {
        Valid valid = validate(request);
        VehicleExpense expense = new VehicleExpense(valid.vehicle(), valid.date(), valid.category(),
                request.description().trim(), request.amount());
        return toDto(vehicleExpenseRepository.save(expense));
    }

    @Transactional
    public ExpenseDto update(String id, VehicleExpenseRequest request) {
        VehicleExpense expense = findOperating(id);
        Valid valid = validate(request);
        expense.update(valid.vehicle(), valid.date(), valid.category(), request.description().trim(), request.amount());
        return toDto(expense);
    }

    @Transactional
    public void delete(String id) {
        vehicleExpenseRepository.delete(findOperating(id));
    }

    /** Un id de gasto de OT tampoco existe acá: esos se manejan desde su orden de trabajo. */
    private VehicleExpense findOperating(String id) {
        return Uuids.parse(id).flatMap(vehicleExpenseRepository::findById)
                .orElseThrow(() -> new ExpenseNotFoundException(id));
    }

    private record Valid(Vehicle vehicle, LocalDate date, VehicleExpenseCategory category) {
    }

    private Valid validate(VehicleExpenseRequest request) {
        List<FieldValidationErrorDetail> details = new ArrayList<>();
        if (request.vehicleId() == null || request.vehicleId().isBlank()) {
            details.add(new FieldValidationErrorDetail("vehicleId", "Obligatorio"));
        }
        LocalDate date = parseDate(request.date(), details);
        VehicleExpenseCategory category = VehicleExpenseCategory.fromJson(request.category());
        if (category == null) {
            details.add(new FieldValidationErrorDetail("category",
                    "Debe ser 'combustible', 'seguro', 'patente', 'vtv', 'peajes' u 'otro'"));
        }
        if (request.description() == null || request.description().isBlank()) {
            details.add(new FieldValidationErrorDetail("description", "Obligatoria"));
        }
        validateAmount(request.amount(), details);
        if (!details.isEmpty()) {
            throw new WorkOrderValidationException("Datos inválidos para el gasto", details);
        }

        Vehicle vehicle = Uuids.parse(request.vehicleId().trim()).flatMap(vehicleRepository::findById)
                .orElseThrow(() -> new VehicleNotFoundException(request.vehicleId()));
        return new Valid(vehicle, date, category);
    }

    private static LocalDate parseDate(String value, List<FieldValidationErrorDetail> details) {
        if (value == null || value.isBlank()) {
            details.add(new FieldValidationErrorDetail("date", "Obligatoria"));
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(value.trim());
            if (date.isAfter(LocalDate.now(FLEET_ZONE))) {
                details.add(new FieldValidationErrorDetail("date", "No puede ser una fecha futura"));
            }
            return date;
        } catch (DateTimeParseException e) {
            details.add(new FieldValidationErrorDetail("date", "Fecha inválida, formato esperado YYYY-MM-DD"));
            return null;
        }
    }

    private static void validateAmount(BigDecimal amount, List<FieldValidationErrorDetail> details) {
        if (amount == null || amount.signum() <= 0) {
            details.add(new FieldValidationErrorDetail("amount", "Debe ser mayor a 0"));
        } else if (amount.compareTo(MAX_AMOUNT) > 0) {
            details.add(new FieldValidationErrorDetail("amount", "Es demasiado alto"));
        } else if (amount.stripTrailingZeros().scale() > 2) {
            details.add(new FieldValidationErrorDetail("amount", "Hasta 2 decimales"));
        }
    }

    private Stream<ExpenseDto> workOrderExpenses(DateRange range) {
        List<WorkOrderExpense> expenses = expenseRepository.findInRangeWithWorkOrder(range.from(), range.to());
        Map<UUID, String> plateByVehicleId = vehicleRepository
                .findAllById(expenses.stream().map(e -> e.getWorkOrder().getVehicleId()).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Vehicle::getId, Vehicle::getPlate));
        return expenses.stream().map(e -> toDto(e, plateByVehicleId.get(e.getWorkOrder().getVehicleId())));
    }

    /** El repositorio corta por día; el corte exacto [from, to) es sobre el instante que sale en `date`. */
    private Stream<ExpenseDto> operatingExpenses(DateRange range) {
        LocalDate firstDay = range.from().atZone(FLEET_ZONE).toLocalDate();
        LocalDate lastDay = range.to().atZone(FLEET_ZONE).toLocalDate();
        return vehicleExpenseRepository.findInRangeWithVehicle(firstDay, lastDay).stream()
                .filter(e -> {
                    Instant date = instantOf(e);
                    return !date.isBefore(range.from()) && date.isBefore(range.to());
                })
                .map(ExpenseService::toDto);
    }

    private static Instant instantOf(VehicleExpense expense) {
        return expense.getDate().atStartOfDay(FLEET_ZONE).toInstant();
    }

    private static ExpenseDto toDto(WorkOrderExpense expense, String plate) {
        WorkOrder workOrder = expense.getWorkOrder();
        return new ExpenseDto(
                expense.getId().toString(),
                expense.getCreatedAt().toString(),
                workOrder.getVehicleId().toString(),
                plate,
                expense.getCategory().toJson(),
                expense.getDescription(),
                expense.getAmount(),
                workOrder.getId().toString(),
                workOrder.getTitle(),
                workOrder.getStatus().toJson(),
                originOf(workOrder),
                workOrder.getExecutionType().toJson(),
                expense.getReceiptNumber(),
                expense.getReceiptPhotoUrl()
        );
    }

    private static ExpenseDto toDto(VehicleExpense expense) {
        return new ExpenseDto(
                expense.getId().toString(),
                instantOf(expense).toString(),
                expense.getVehicle().getId().toString(),
                expense.getVehicle().getPlate(),
                expense.getCategory().toJson(),
                expense.getDescription(),
                expense.getAmount(),
                null,
                null,
                null,
                OPERATING_ORIGIN,
                null,
                null,
                null
        );
    }

    // Una OT nacida del calendario conserva el plan o el defecto que la originó: eso es lo que
    // dice si el gasto fue preventivo o correctivo, no el sourceType.
    private static String originOf(WorkOrder workOrder) {
        if (workOrder.getAssignmentId() != null) {
            return "plan";
        }
        return workOrder.getDefectId() != null ? "defect" : "manual";
    }
}
