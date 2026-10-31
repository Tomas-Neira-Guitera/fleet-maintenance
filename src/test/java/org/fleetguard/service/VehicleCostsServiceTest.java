package org.fleetguard.service;

import org.fleetguard.dto.VehicleCostsDto;
import org.fleetguard.entity.Inspection;
import org.fleetguard.entity.InspectionType;
import org.fleetguard.entity.MaintenanceCompletion;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.VehicleExpense;
import org.fleetguard.entity.VehicleExpenseCategory;
import org.fleetguard.entity.WorkOrder;
import org.fleetguard.entity.WorkOrderExecutionType;
import org.fleetguard.entity.WorkOrderExpense;
import org.fleetguard.entity.WorkOrderExpenseCategory;
import org.fleetguard.entity.WorkOrderSourceType;
import org.fleetguard.exception.VehicleNotFoundException;
import org.fleetguard.repository.InspectionRepository;
import org.fleetguard.repository.MaintenanceCompletionRepository;
import org.fleetguard.repository.VehicleExpenseRepository;
import org.fleetguard.repository.VehicleRepository;
import org.fleetguard.repository.WorkOrderExpenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Costo de mantenimiento de un vehículo (CAM-105): totales de gasto y costo por km, que se calcula
 * desde el registro de km más antiguo entre sus inspecciones y mantenimientos hechos.
 */
class VehicleCostsServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-14T12:00:00Z");

    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final WorkOrderExpenseRepository expenseRepository = mock(WorkOrderExpenseRepository.class);
    private final VehicleExpenseRepository vehicleExpenseRepository = mock(VehicleExpenseRepository.class);
    private final InspectionRepository inspectionRepository = mock(InspectionRepository.class);
    private final MaintenanceCompletionRepository completionRepository = mock(MaintenanceCompletionRepository.class);
    private final VehicleCostsService service = new VehicleCostsService(vehicleRepository, expenseRepository,
            vehicleExpenseRepository, inspectionRepository, completionRepository);

    private Vehicle truck;
    private String truckId;

    private static void setField(Object entity, String name, Object value) throws Exception {
        Field field = entity.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(entity, value);
    }

    private WorkOrderExpense expense(WorkOrder workOrder, String amount, String createdAt) throws Exception {
        WorkOrderExpense expense = new WorkOrderExpense(workOrder, WorkOrderExpenseCategory.REPUESTO, "Repuesto",
                new BigDecimal(amount));
        setField(expense, "id", UUID.randomUUID());
        setField(expense, "createdAt", Instant.parse(createdAt));
        return expense;
    }

    private Inspection inspection(String timestamp, Double odometerKm) {
        return new Inspection(null, truck.getId(), "d-1", "Chofer", InspectionType.PRE_TRIP, Instant.parse(timestamp),
                odometerKm, null, false);
    }

    private static MaintenanceCompletion completion(String completedAt, Long completedKm) {
        return new MaintenanceCompletion(null, LocalDate.parse(completedAt), completedKm, null, null);
    }

    @BeforeEach
    void setUp() throws Exception {
        truck = new Vehicle("AB123CD", "Ford", "Cargo");
        setField(truck, "id", UUID.randomUUID());
        truck.setOdometerKm(60000);
        truckId = truck.getId().toString();
        when(vehicleRepository.findById(truck.getId())).thenReturn(Optional.of(truck));
        when(inspectionRepository.findByVehicleIdOrderByTimestampDesc(any())).thenReturn(List.of());
        when(completionRepository.findByVehicleIdWithPlan(any())).thenReturn(List.of());
    }

    @Test
    void sumaLosGastosDeDosOtsEnTotalYEnLosUltimos12Meses() throws Exception {
        WorkOrder oilChange = new WorkOrder(truck.getId(), WorkOrderSourceType.MANUAL, null, null, null,
                "Cambio de aceite", null, WorkOrderExecutionType.INTERNO, null, null, null);
        WorkOrder brakes = new WorkOrder(truck.getId(), WorkOrderSourceType.MANUAL, null, null, null,
                "Frenos", null, WorkOrderExecutionType.EXTERNO, null, null, null);
        when(expenseRepository.findByVehicleId(truck.getId())).thenReturn(List.of(
                expense(oilChange, "120000.50", "2026-03-10T13:00:00Z"),
                expense(brakes, "250000.00", "2026-09-20T12:00:00Z"),
                // Hace más de 12 meses: suma al total, no al último año.
                expense(brakes, "30000.00", "2025-10-13T12:00:00Z")));

        VehicleCostsDto costs = service.getCosts(truckId, NOW);

        assertEquals(new BigDecimal("400000.50"), costs.totalAmount());
        assertEquals(new BigDecimal("370000.50"), costs.last12MonthsAmount());
        assertEquals(3, costs.expenseCount());
    }

    @Test
    void losGastosOperativosSumanAlTotalAlUltimoAnioYAlCostoPorKm() throws Exception {
        WorkOrder brakes = new WorkOrder(truck.getId(), WorkOrderSourceType.MANUAL, null, null, null,
                "Frenos", null, WorkOrderExecutionType.EXTERNO, null, null, null);
        when(expenseRepository.findByVehicleId(truck.getId())).thenReturn(List.of(
                expense(brakes, "100000.00", "2026-09-20T12:00:00Z")));
        when(inspectionRepository.findByVehicleIdOrderByTimestampDesc(truck.getId()))
                .thenReturn(List.of(inspection("2026-01-01T12:00:00Z", 50000.0)));
        when(vehicleExpenseRepository.findByVehicle_Id(truck.getId())).thenReturn(List.of(
                new VehicleExpense(truck, LocalDate.parse("2026-10-01"), VehicleExpenseCategory.COMBUSTIBLE, "Gasoil",
                        new BigDecimal("50000.00")),
                // El primer día de la ventana todavía entra; el anterior, no.
                new VehicleExpense(truck, LocalDate.parse("2025-10-14"), VehicleExpenseCategory.SEGURO, "Seguro",
                        new BigDecimal("30000.00")),
                new VehicleExpense(truck, LocalDate.parse("2025-10-13"), VehicleExpenseCategory.PATENTE, "Patente",
                        new BigDecimal("20000.00"))));

        VehicleCostsDto costs = service.getCosts(truckId, NOW);

        assertEquals(new BigDecimal("200000.00"), costs.totalAmount());
        assertEquals(new BigDecimal("180000.00"), costs.last12MonthsAmount());
        assertEquals(4, costs.expenseCount());
        assertEquals(new BigDecimal("20.00"), costs.costPerKm());
    }

    @Test
    void elCostoPorKmArrancaDelRegistroMasAntiguoEntreInspeccionesYMantenimientos() throws Exception {
        WorkOrder workOrder = new WorkOrder(truck.getId(), WorkOrderSourceType.MANUAL, null, null, null, "Frenos",
                null, WorkOrderExecutionType.EXTERNO, null, null, null);
        when(expenseRepository.findByVehicleId(truck.getId()))
                .thenReturn(List.of(expense(workOrder, "100000.00", "2026-09-20T12:00:00Z")));
        when(inspectionRepository.findByVehicleIdOrderByTimestampDesc(truck.getId()))
                .thenReturn(List.of(inspection("2026-09-01T10:00:00Z", 58000.0),
                        inspection("2026-01-01T10:00:00Z", null),
                        inspection("2026-03-02T10:00:00Z", 54000.7)));
        when(completionRepository.findByVehicleIdWithPlan(truck.getId()))
                .thenReturn(List.of(completion("2026-05-01", 55000L), completion("2026-02-01", null)));

        VehicleCostsDto costs = service.getCosts(truckId, NOW);

        assertEquals(60000, costs.currentKm());
        assertEquals(54000L, costs.firstKnownKm());
        assertEquals(6000L, costs.kmDriven());
        // 100.000 / 6.000 = 16,666...
        assertEquals(new BigDecimal("16.67"), costs.costPerKm());
    }

    @Test
    void unMantenimientoHechoTambienSirveDePrimerRegistro() {
        when(expenseRepository.findByVehicleId(truck.getId())).thenReturn(List.of());
        when(inspectionRepository.findByVehicleIdOrderByTimestampDesc(truck.getId()))
                .thenReturn(List.of(inspection("2026-06-01T10:00:00Z", 52000.0)));
        when(completionRepository.findByVehicleIdWithPlan(truck.getId()))
                .thenReturn(List.of(completion("2026-04-01", 50000L)));

        VehicleCostsDto costs = service.getCosts(truckId, NOW);

        assertEquals(50000L, costs.firstKnownKm());
        assertEquals(10000L, costs.kmDriven());
    }

    @Test
    void unaInspeccionRecienteConUnKmMalTipeadoNoMueveElPuntoDePartida() {
        // Pasó en la base de demo: una inspección de hoy con 310 km en un vehículo de 309.400 km.
        when(expenseRepository.findByVehicleId(truck.getId())).thenReturn(List.of());
        when(inspectionRepository.findByVehicleIdOrderByTimestampDesc(truck.getId()))
                .thenReturn(List.of(inspection("2026-10-14T09:00:00Z", 310.0),
                        inspection("2026-04-16T10:00:00Z", 55000.0)));

        VehicleCostsDto costs = service.getCosts(truckId, NOW);

        assertEquals(55000L, costs.firstKnownKm());
        assertEquals(5000L, costs.kmDriven());
    }

    @Test
    void losUltimos12MesesArrancanALaMedianocheArgentinaDelMismoDiaDeHaceUnAnio() throws Exception {
        // NOW es el 14/10/2026 a las 09:00 en Argentina: la ventana arranca el 14/10/2025 a las 00:00 (03:00Z).
        WorkOrder workOrder = new WorkOrder(truck.getId(), WorkOrderSourceType.MANUAL, null, null, null, "Frenos",
                null, WorkOrderExecutionType.EXTERNO, null, null, null);
        when(expenseRepository.findByVehicleId(truck.getId())).thenReturn(List.of(
                expense(workOrder, "1000.00", "2025-10-14T03:00:00Z"),
                expense(workOrder, "50.00", "2025-10-14T02:59:59Z")));

        VehicleCostsDto costs = service.getCosts(truckId, NOW);

        assertEquals(new BigDecimal("1050.00"), costs.totalAmount());
        assertEquals(new BigDecimal("1000.00"), costs.last12MonthsAmount());
    }

    @Test
    void siDosRegistrosCaenEnElMismoMomentoGanaElMenorKm() {
        when(expenseRepository.findByVehicleId(truck.getId())).thenReturn(List.of());
        when(inspectionRepository.findByVehicleIdOrderByTimestampDesc(truck.getId()))
                .thenReturn(List.of(inspection("2026-04-01T00:00:00Z", 51000.0)));
        when(completionRepository.findByVehicleIdWithPlan(truck.getId()))
                .thenReturn(List.of(completion("2026-04-01", 50500L)));

        VehicleCostsDto costs = service.getCosts(truckId, NOW);

        assertEquals(50500L, costs.firstKnownKm());
    }

    @Test
    void sinGastosDevuelveCeroYNoUnError() {
        when(expenseRepository.findByVehicleId(truck.getId())).thenReturn(List.of());
        when(inspectionRepository.findByVehicleIdOrderByTimestampDesc(truck.getId()))
                .thenReturn(List.of(inspection("2026-04-16T10:00:00Z", 50000.0)));

        VehicleCostsDto costs = service.getCosts(truckId, NOW);

        assertEquals(BigDecimal.ZERO, costs.totalAmount());
        assertEquals(BigDecimal.ZERO, costs.last12MonthsAmount());
        assertEquals(0, costs.expenseCount());
        assertEquals(new BigDecimal("0.00"), costs.costPerKm());
    }

    @Test
    void sinRegistrosDeKmElCostoPorKmQuedaVacio() throws Exception {
        WorkOrder workOrder = new WorkOrder(truck.getId(), WorkOrderSourceType.MANUAL, null, null, null, "Frenos",
                null, WorkOrderExecutionType.EXTERNO, null, null, null);
        when(expenseRepository.findByVehicleId(truck.getId()))
                .thenReturn(List.of(expense(workOrder, "100000.00", "2026-09-20T12:00:00Z")));

        VehicleCostsDto costs = service.getCosts(truckId, NOW);

        assertEquals(new BigDecimal("100000.00"), costs.totalAmount());
        assertNull(costs.firstKnownKm());
        assertNull(costs.kmDriven());
        assertNull(costs.costPerKm());
    }

    @Test
    void siElKmNoAvanzoDesdeElPrimerRegistroElCostoPorKmQuedaVacio() {
        when(expenseRepository.findByVehicleId(truck.getId())).thenReturn(List.of());
        when(inspectionRepository.findByVehicleIdOrderByTimestampDesc(truck.getId()))
                .thenReturn(List.of(inspection("2026-04-16T10:00:00Z", 60000.0)));

        VehicleCostsDto costs = service.getCosts(truckId, NOW);

        assertEquals(60000L, costs.firstKnownKm());
        assertNull(costs.kmDriven());
        assertNull(costs.costPerKm());
    }

    @Test
    void unVehiculoQueNoExisteOUnIdInvalidoDan404() {
        when(vehicleRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(VehicleNotFoundException.class, () -> service.getCosts(UUID.randomUUID().toString(), NOW));
        assertThrows(VehicleNotFoundException.class, () -> service.getCosts("no-es-un-uuid", NOW));
        verifyNoInteractions(expenseRepository);
    }
}
