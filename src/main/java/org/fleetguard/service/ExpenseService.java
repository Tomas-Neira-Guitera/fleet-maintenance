package org.fleetguard.service;

import org.fleetguard.dto.ExpenseDto;
import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.WorkOrder;
import org.fleetguard.entity.WorkOrderExpense;
import org.fleetguard.entity.WorkOrderExpenseCategory;
import org.fleetguard.exception.WorkOrderValidationException;
import org.fleetguard.repository.VehicleRepository;
import org.fleetguard.repository.WorkOrderExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lógica de GET /api/expenses (CAM-52): los gastos que ya se cargan dentro de cada orden de
 * trabajo (CAM-63), todos juntos. Solo lectura: no hay modelo nuevo. Los totales los calcula
 * el cliente sobre este listado.
 */
@Service
public class ExpenseService {

    private final WorkOrderExpenseRepository expenseRepository;
    private final VehicleRepository vehicleRepository;

    public ExpenseService(WorkOrderExpenseRepository expenseRepository, VehicleRepository vehicleRepository) {
        this.expenseRepository = expenseRepository;
        this.vehicleRepository = vehicleRepository;
    }

    /** Todos los filtros son opcionales. Un vehicleId o category que no existe da lista vacía, no error. */
    @Transactional(readOnly = true)
    public List<ExpenseDto> list(String vehicleIdParam, String categoryParam, String from, String to) {
        List<FieldValidationErrorDetail> details = new ArrayList<>();
        DateRange range = DateRange.parse(from, to, details);
        if (!details.isEmpty()) {
            throw new WorkOrderValidationException("Período inválido", details);
        }

        List<WorkOrderExpense> expenses = expenseRepository.findInRangeWithWorkOrder(range.from(), range.to());
        if (vehicleIdParam != null && !vehicleIdParam.isBlank()) {
            UUID vehicleId = Uuids.parse(vehicleIdParam.trim()).orElse(null);
            expenses = expenses.stream().filter(e -> e.getWorkOrder().getVehicleId().equals(vehicleId)).toList();
        }
        if (categoryParam != null && !categoryParam.isBlank()) {
            WorkOrderExpenseCategory category = WorkOrderExpenseCategory.fromJson(categoryParam);
            expenses = expenses.stream().filter(e -> e.getCategory() == category).toList();
        }

        Map<UUID, String> plateByVehicleId = vehicleRepository
                .findAllById(expenses.stream().map(e -> e.getWorkOrder().getVehicleId()).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Vehicle::getId, Vehicle::getPlate));

        return expenses.stream().map(e -> toDto(e, plateByVehicleId.get(e.getWorkOrder().getVehicleId()))).toList();
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
                workOrder.getExecutionType().toJson()
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
