package org.fleetguard.service;

import org.fleetguard.dto.ExpenseDto;
import org.fleetguard.dto.VehicleExpenseRequest;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.VehicleExpense;
import org.fleetguard.entity.VehicleExpenseCategory;
import org.fleetguard.entity.WorkOrder;
import org.fleetguard.entity.WorkOrderExecutionType;
import org.fleetguard.entity.WorkOrderExpense;
import org.fleetguard.entity.WorkOrderExpenseCategory;
import org.fleetguard.entity.WorkOrderSourceType;
import org.fleetguard.exception.ExpenseNotFoundException;
import org.fleetguard.exception.VehicleNotFoundException;
import org.fleetguard.exception.WorkOrderValidationException;
import org.fleetguard.repository.VehicleExpenseRepository;
import org.fleetguard.repository.VehicleRepository;
import org.fleetguard.repository.WorkOrderExpenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Gastos operativos del vehículo (CAM-109): alta, edición y baja, y el listado de GET /api/expenses
 * que los une con los gastos de OT.
 */
class VehicleExpenseServiceTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"));

    private final WorkOrderExpenseRepository expenseRepository = mock(WorkOrderExpenseRepository.class);
    private final VehicleExpenseRepository vehicleExpenseRepository = mock(VehicleExpenseRepository.class);
    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final ExpenseService service = new ExpenseService(expenseRepository, vehicleExpenseRepository,
            vehicleRepository);

    private Vehicle truck;
    private VehicleExpense fuel;

    private static void setField(Object entity, String name, Object value) throws Exception {
        Field field = entity.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(entity, value);
    }

    private VehicleExpense operating(String date, VehicleExpenseCategory category, String amount) throws Exception {
        VehicleExpense expense = new VehicleExpense(truck, LocalDate.parse(date), category, "Gasto", new BigDecimal(amount));
        setField(expense, "id", UUID.randomUUID());
        return expense;
    }

    private WorkOrderExpense workOrderExpense(String createdAt) throws Exception {
        WorkOrder workOrder = new WorkOrder(truck.getId(), WorkOrderSourceType.MANUAL, null, null, null, "Frenos", null,
                WorkOrderExecutionType.INTERNO, null, null, null);
        setField(workOrder, "id", UUID.randomUUID());
        WorkOrderExpense expense = new WorkOrderExpense(workOrder, WorkOrderExpenseCategory.OTRO, "Grúa",
                new BigDecimal("10.00"));
        setField(expense, "id", UUID.randomUUID());
        setField(expense, "createdAt", Instant.parse(createdAt));
        return expense;
    }

    private VehicleExpenseRequest request(String date, String category, String description, String amount) {
        return new VehicleExpenseRequest(truck.getId().toString(), date, category, description,
                amount == null ? null : new BigDecimal(amount));
    }

    private List<String> invalidFields(VehicleExpenseRequest request) {
        WorkOrderValidationException ex = assertThrows(WorkOrderValidationException.class, () -> service.create(request));
        verify(vehicleExpenseRepository, never()).save(any());
        return ex.getDetails().stream().map(d -> d.field()).toList();
    }

    @BeforeEach
    void setUp() throws Exception {
        truck = new Vehicle("AB123CD", "Ford", "Cargo");
        setField(truck, "id", UUID.randomUUID());
        fuel = operating("2026-10-05", VehicleExpenseCategory.COMBUSTIBLE, "50000.00");
        when(vehicleRepository.findById(truck.getId())).thenReturn(Optional.of(truck));
        when(vehicleRepository.findAllById(any())).thenReturn(List.of(truck));
        when(vehicleExpenseRepository.findById(fuel.getId())).thenReturn(Optional.of(fuel));
        when(vehicleExpenseRepository.save(any())).thenAnswer(call -> {
            VehicleExpense saved = call.getArgument(0);
            setField(saved, "id", UUID.randomUUID());
            return saved;
        });
    }

    @Test
    void cargaUnGastoSinOtYDevuelveSuVehiculoConLaFechaEnMedianocheArgentina() {
        ExpenseDto created = service.create(request("2026-10-05", "combustible", " Gasoil ", "45000.50"));

        assertEquals("2026-10-05T03:00:00Z", created.date());
        assertEquals("AB123CD", created.plate());
        assertEquals(truck.getId().toString(), created.vehicleId());
        assertEquals("combustible", created.category());
        assertEquals("Gasoil", created.description());
        assertEquals(new BigDecimal("45000.50"), created.amount());
        assertEquals("operating", created.origin());
        assertNull(created.workOrderId());
        assertNull(created.workOrderTitle());
        assertNull(created.workOrderStatus());
        assertNull(created.executionType());
    }

    @Test
    void aceptaHoyYUnaFechaPasadaPeroNoUnaFutura() {
        service.create(request(TODAY.toString(), "peajes", "Peaje", "100"));
        service.create(request(TODAY.minusMonths(2).toString(), "seguro", "Póliza", "100"));

        assertEquals(List.of("date"), assertThrows(WorkOrderValidationException.class,
                () -> service.create(request(TODAY.plusDays(1).toString(), "peajes", "Peaje", "100")))
                .getDetails().stream().map(d -> d.field()).toList());
    }

    @Test
    void junta422PorCadaCampoInvalido() {
        assertEquals(List.of("vehicleId", "date", "category", "description", "amount"), invalidFields(
                new VehicleExpenseRequest(" ", null, "repuesto", " ", null)));
        assertEquals(List.of("date"), invalidFields(request("05/10/2026", "vtv", "VTV", "100")));
    }

    @Test
    void elMontoTieneQueEntrarEnLaColumna() {
        assertEquals(List.of("amount"), invalidFields(request("2026-10-05", "vtv", "VTV", "0")));
        assertEquals(List.of("amount"), invalidFields(request("2026-10-05", "vtv", "VTV", "-1")));
        assertEquals(List.of("amount"), invalidFields(request("2026-10-05", "vtv", "VTV", "10.123")));
        assertEquals(List.of("amount"), invalidFields(request("2026-10-05", "vtv", "VTV", "10000000000")));
        // Ceros de más no son decimales de más.
        service.create(request("2026-10-05", "vtv", "VTV", "10.5000"));
    }

    @Test
    void unVehiculoQueNoExisteDa404() {
        VehicleExpenseRequest unknown = new VehicleExpenseRequest(UUID.randomUUID().toString(), "2026-10-05", "vtv", "VTV",
                BigDecimal.TEN);
        VehicleExpenseRequest malformed = new VehicleExpenseRequest("no-es-un-uuid", "2026-10-05", "vtv", "VTV",
                BigDecimal.TEN);

        assertThrows(VehicleNotFoundException.class, () -> service.create(unknown));
        assertThrows(VehicleNotFoundException.class, () -> service.create(malformed));
    }

    @Test
    void editarReemplazaTodosLosCampos() {
        ExpenseDto updated = service.update(fuel.getId().toString(), request("2026-09-30", "peajes", "Autopista", "1200"));

        assertEquals("2026-09-30T03:00:00Z", updated.date());
        assertEquals("peajes", updated.category());
        assertEquals("Autopista", updated.description());
        assertEquals(new BigDecimal("1200"), fuel.getAmount());
    }

    @Test
    void borrarSoloAlcanzaAUnGastoOperativo() {
        service.delete(fuel.getId().toString());
        verify(vehicleExpenseRepository).delete(fuel);

        String unknown = UUID.randomUUID().toString();
        when(vehicleExpenseRepository.findById(UUID.fromString(unknown))).thenReturn(Optional.empty());
        assertThrows(ExpenseNotFoundException.class, () -> service.delete(unknown));
        assertThrows(ExpenseNotFoundException.class, () -> service.delete("no-es-un-uuid"));
        assertThrows(ExpenseNotFoundException.class,
                () -> service.update(unknown, request("2026-10-05", "vtv", "VTV", "100")));
    }

    @Test
    void elListadoUneLosDosTiposDelMasRecienteAlMasAntiguo() throws Exception {
        when(expenseRepository.findInRangeWithWorkOrder(any(), any())).thenReturn(List.of(
                workOrderExpense("2026-10-08T12:00:00Z"), workOrderExpense("2026-10-01T12:00:00Z")));
        when(vehicleExpenseRepository.findInRangeWithVehicle(any(), any())).thenReturn(List.of(fuel));

        List<ExpenseDto> result = service.list(null, null, null, null, null);

        assertEquals(List.of("manual", "operating", "manual"), result.stream().map(ExpenseDto::origin).toList());
        assertEquals(List.of("2026-10-08T12:00:00Z", "2026-10-05T03:00:00Z", "2026-10-01T12:00:00Z"),
                result.stream().map(ExpenseDto::date).toList());
    }

    @Test
    void losFiltrosAlcanzanALosDosTipos() throws Exception {
        when(expenseRepository.findInRangeWithWorkOrder(any(), any())).thenReturn(List.of(
                workOrderExpense("2026-10-08T12:00:00Z")));
        when(vehicleExpenseRepository.findInRangeWithVehicle(any(), any())).thenReturn(List.of(
                fuel, operating("2026-10-06", VehicleExpenseCategory.OTRO, "10.00")));

        assertEquals(List.of("operating", "operating"),
                service.list(null, null, "operating", null, null).stream().map(ExpenseDto::origin).toList());
        assertEquals(1, service.list(null, null, "manual", null, null).size());
        assertEquals(1, service.list(null, "combustible", null, null, null).size());
        // "otro" es la misma categoría en los dos tipos.
        assertEquals(2, service.list(null, "otro", null, null, null).size());
        assertEquals(3, service.list(truck.getId().toString(), null, null, null, null).size());
        assertTrue(service.list(null, null, "cualquiera", null, null).isEmpty());
    }

    @Test
    void elPeriodoCortaPorElInstanteDeLaMedianocheArgentina() throws Exception {
        when(vehicleExpenseRepository.findInRangeWithVehicle(any(), any())).thenReturn(List.of(
                operating("2025-12-31", VehicleExpenseCategory.PEAJES, "10.00"),
                operating("2026-01-01", VehicleExpenseCategory.PEAJES, "20.00"),
                operating("2026-12-31", VehicleExpenseCategory.PEAJES, "30.00"),
                operating("2027-01-01", VehicleExpenseCategory.PEAJES, "40.00")));

        // El año 2026 en hora argentina: `from` entra y `to` no.
        List<ExpenseDto> result = service.list(null, null, null, "2026-01-01T03:00:00Z", "2027-01-01T03:00:00Z");

        verify(vehicleExpenseRepository).findInRangeWithVehicle(LocalDate.parse("2026-01-01"), LocalDate.parse("2027-01-01"));
        assertEquals(List.of(new BigDecimal("30.00"), new BigDecimal("20.00")),
                result.stream().map(ExpenseDto::amount).toList());
    }
}
