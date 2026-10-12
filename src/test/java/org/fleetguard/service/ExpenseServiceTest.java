package org.fleetguard.service;

import org.fleetguard.dto.ExpenseDto;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.WorkOrder;
import org.fleetguard.entity.WorkOrderExecutionType;
import org.fleetguard.entity.WorkOrderExpense;
import org.fleetguard.entity.WorkOrderExpenseCategory;
import org.fleetguard.entity.WorkOrderSourceType;
import org.fleetguard.exception.WorkOrderValidationException;
import org.fleetguard.repository.VehicleRepository;
import org.fleetguard.repository.WorkOrderExpenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Vista de Gastos (CAM-52): todos los gastos cargados en cualquier OT, con filtros por vehículo,
 * categoría y fechas. El repositorio ya entrega el período ordenado del más reciente al más antiguo.
 */
class ExpenseServiceTest {

    private final WorkOrderExpenseRepository expenseRepository = mock(WorkOrderExpenseRepository.class);
    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final ExpenseService service = new ExpenseService(expenseRepository, vehicleRepository);

    private Vehicle truck;
    private Vehicle van;
    private WorkOrderExpense aceite;
    private WorkOrderExpense manoDeObra;
    private WorkOrderExpense grua;

    private static void setField(Object entity, String name, Object value) throws Exception {
        Field field = entity.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(entity, value);
    }

    private static Vehicle vehicle(String plate) throws Exception {
        Vehicle vehicle = new Vehicle(plate, "Ford", "Cargo");
        setField(vehicle, "id", UUID.randomUUID());
        return vehicle;
    }

    /** assignmentId / defectId deciden el origen: plan (preventivo), defecto (correctivo) o manual. */
    private static WorkOrder workOrder(Vehicle vehicle, String title, UUID assignmentId, UUID defectId,
                                       WorkOrderExecutionType executionType) throws Exception {
        WorkOrder workOrder = new WorkOrder(vehicle.getId(), WorkOrderSourceType.MANUAL, null, defectId, assignmentId,
                title, null, executionType, null, null, null);
        setField(workOrder, "id", UUID.randomUUID());
        return workOrder;
    }

    private static WorkOrderExpense expense(WorkOrder workOrder, WorkOrderExpenseCategory category, String description,
                                            String amount, String createdAt) throws Exception {
        WorkOrderExpense expense = new WorkOrderExpense(workOrder, category, description, new BigDecimal(amount));
        setField(expense, "id", UUID.randomUUID());
        setField(expense, "createdAt", Instant.parse(createdAt));
        return expense;
    }

    @BeforeEach
    void setUp() throws Exception {
        truck = vehicle("AB123CD");
        van = vehicle("AC456EF");
        WorkOrder preventiva = workOrder(truck, "Cambio de aceite", UUID.randomUUID(), null, WorkOrderExecutionType.INTERNO);
        WorkOrder correctiva = workOrder(van, "Pérdida de aire", null, UUID.randomUUID(), WorkOrderExecutionType.EXTERNO);
        WorkOrder suelta = workOrder(van, "Traslado al taller", null, null, WorkOrderExecutionType.EXTERNO);
        correctiva.start();
        preventiva.finalizeOrder("Hecho");

        grua = expense(suelta, WorkOrderExpenseCategory.OTRO, "Grúa", "90000.00", "2026-10-05T15:00:00Z");
        manoDeObra = expense(correctiva, WorkOrderExpenseCategory.MANO_DE_OBRA, "Taller externo", "250000.00", "2026-09-20T12:00:00Z");
        aceite = expense(preventiva, WorkOrderExpenseCategory.REPUESTO, "Aceite 10W40", "120000.50", "2026-03-10T13:00:00Z");

        when(expenseRepository.findInRangeWithWorkOrder(any(), any())).thenReturn(List.of(grua, manoDeObra, aceite));
        when(vehicleRepository.findAllById(any())).thenReturn(List.of(truck, van));
    }

    @Test
    void cadaGastoTraeSuVehiculoSuOtYElOrigenDeEsaOt() {
        List<ExpenseDto> result = service.list(null, null, null, null);

        assertEquals(3, result.size());
        ExpenseDto first = result.get(0);
        assertEquals("2026-10-05T15:00:00Z", first.date());
        assertEquals("AC456EF", first.plate());
        assertEquals(van.getId().toString(), first.vehicleId());
        assertEquals("otro", first.category());
        assertEquals(new BigDecimal("90000.00"), first.amount());
        assertEquals(grua.getWorkOrder().getId().toString(), first.workOrderId());
        assertEquals("Traslado al taller", first.workOrderTitle());
        assertEquals("asignada", first.workOrderStatus());
        assertEquals("externo", first.executionType());
        // El origen es lo que separa preventivo de correctivo en la vista.
        assertEquals("manual", first.origin());
        assertEquals("defect", result.get(1).origin());
        assertEquals("en_proceso", result.get(1).workOrderStatus());
        assertEquals("plan", result.get(2).origin());
        assertEquals("finalizada", result.get(2).workOrderStatus());
        assertEquals("AB123CD", result.get(2).plate());
    }

    @Test
    void elFiltroPorVehiculoDejaSoloSusGastos() {
        List<ExpenseDto> result = service.list(van.getId().toString(), null, null, null);

        assertEquals(List.of("Grúa", "Taller externo"), result.stream().map(ExpenseDto::description).toList());
    }

    @Test
    void unVehiculoInexistenteOMalformadoDaListaVaciaYNoError() {
        assertTrue(service.list(UUID.randomUUID().toString(), null, null, null).isEmpty());
        assertTrue(service.list("no-es-un-uuid", null, null, null).isEmpty());
    }

    @Test
    void elFiltroPorCategoriaDejaSoloEsaCategoria() {
        List<ExpenseDto> result = service.list(null, "repuesto", null, null);

        assertEquals(1, result.size());
        assertEquals("Aceite 10W40", result.get(0).description());
        assertTrue(service.list(null, "viaticos", null, null).isEmpty());
    }

    @Test
    void losFiltrosSeCombinan() {
        assertEquals(List.of("Taller externo"), service.list(van.getId().toString(), "mano_de_obra", null, null)
                .stream().map(ExpenseDto::description).toList());
        assertTrue(service.list(truck.getId().toString(), "mano_de_obra", null, null).isEmpty());
    }

    @Test
    void sinFechasElPeriodoQuedaAbierto() {
        service.list(null, null, null, " ");

        verify(expenseRepository).findInRangeWithWorkOrder(Instant.EPOCH, Instant.parse("9999-01-01T00:00:00Z"));
    }

    @Test
    void conFechasSolasElHastaIncluyeEseDiaEntero() {
        service.list(null, null, "2026-01-01", "2026-12-31");

        verify(expenseRepository).findInRangeWithWorkOrder(
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2027-01-01T00:00:00Z"));
    }

    @Test
    void conInstantesCompletosElPeriodoEsExactoYElHastaNoSeIncluye() {
        // Así lo manda el frontend: la medianoche local (UTC-3) como instante, no como fecha.
        service.list(null, null, "2026-01-01T03:00:00Z", "2027-01-01T03:00:00Z");

        verify(expenseRepository).findInRangeWithWorkOrder(
                Instant.parse("2026-01-01T03:00:00Z"), Instant.parse("2027-01-01T03:00:00Z"));
    }

    @Test
    void unaFechaInvalidaDevuelve422ConElCampo() {
        WorkOrderValidationException ex = assertThrows(WorkOrderValidationException.class,
                () -> service.list(null, null, "ayer", "2026-13-40"));

        assertEquals(List.of("from", "to"), ex.getDetails().stream().map(d -> d.field()).toList());
        verifyNoInteractions(expenseRepository);
    }

    @Test
    void sinGastosEnElPeriodoDevuelveListaVacia() {
        when(expenseRepository.findInRangeWithWorkOrder(any(), any())).thenReturn(List.of());

        assertTrue(service.list(null, null, "2020-01-01", "2020-12-31").isEmpty());
    }
}
