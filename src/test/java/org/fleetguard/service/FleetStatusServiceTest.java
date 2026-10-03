package org.fleetguard.service;

import org.fleetguard.dto.FleetStatusRowDto;
import org.fleetguard.dto.OdometerResultDto;
import org.fleetguard.dto.PagedResponse;
import org.fleetguard.dto.UpdateVehicleRequest;
import org.fleetguard.dto.VehicleSummaryDto;
import org.fleetguard.entity.IntervalType;
import org.fleetguard.entity.MaintenancePlan;
import org.fleetguard.entity.TripStatus;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.VehicleMaintenanceAssignment;
import org.fleetguard.exception.MaintenanceConflictException;
import org.fleetguard.exception.VehicleStateConflictException;
import org.fleetguard.exception.VehicleValidationException;
import org.fleetguard.mapper.VehicleMapper;
import org.fleetguard.repository.TripRepository;
import org.fleetguard.repository.VehicleMaintenanceAssignmentRepository;
import org.fleetguard.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
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
 * Estado de la flota (CAM-40, tabla "Estado de la flota" del dashboard), carga de kilometraje
 * y reglas de edición de vehículos que no cubre VehicleServiceTest. El estado de cada vehículo
 * es el peor de sus planes, y el "próximo mantenimiento" es el más urgente: si eso se calcula
 * mal, el admin ve al día un vehículo que está vencido.
 */
class FleetStatusServiceTest {

    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final TripRepository tripRepository = mock(TripRepository.class);
    private final VehicleMaintenanceAssignmentRepository assignmentRepository = mock(VehicleMaintenanceAssignmentRepository.class);
    private final VehicleService service =
            new VehicleService(vehicleRepository, tripRepository, assignmentRepository, new VehicleMapper());

    private final List<Vehicle> fleet = new ArrayList<>();
    private final List<VehicleMaintenanceAssignment> assignments = new ArrayList<>();

    private static void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    @BeforeEach
    void setUp() {
        when(vehicleRepository.findByActive(true)).thenReturn(fleet);
        when(assignmentRepository.findByVehicleIdIn(any())).thenReturn(assignments);
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Vehicle vehicle(String plate, long km) throws Exception {
        Vehicle vehicle = new Vehicle(plate, "Ford", "Cargo");
        setId(vehicle, UUID.randomUUID());
        vehicle.setOdometerKm(km);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(tripRepository.findFirstByVehicle_IdAndStatus(vehicle.getId(), TripStatus.OPEN)).thenReturn(Optional.empty());
        fleet.add(vehicle);
        return vehicle;
    }

    /** Plan por km asignado con la última vez en lastKm. */
    private VehicleMaintenanceAssignment plan(Vehicle vehicle, String name, int intervalKm, long lastKm) throws Exception {
        MaintenancePlan plan = new MaintenancePlan(name, null, IntervalType.KM, intervalKm, null);
        setId(plan, UUID.randomUUID());
        VehicleMaintenanceAssignment assignment = new VehicleMaintenanceAssignment(vehicle.getId(), plan, lastKm, null);
        setId(assignment, UUID.randomUUID());
        assignment.recalculateNextDue();
        assignments.add(assignment);
        return assignment;
    }

    @Test
    void elEstadoDelVehiculoEsElPeorDeSusPlanesYElProximoEsElMasUrgente() throws Exception {
        Vehicle truck = vehicle("AB123CD", 50000);
        plan(truck, "Rotación de neumáticos", 20000, 45000);   // vence a los 65.000: al día
        plan(truck, "Cambio de aceite", 10000, 39000);         // venció a los 49.000: vencido

        FleetStatusRowDto row = service.getFleetStatus(1, 20, null, true).items().get(0);

        assertEquals("vencido", row.status());
        assertEquals("Cambio de aceite", row.nextMaintenance().name());
        assertEquals(-1000L, row.nextMaintenance().remainingKm());
        assertTrue(row.healthScore() < 100);
    }

    @Test
    void conPlanesDelMismoEstadoGanaElQueConsumioMasDeSuIntervalo() throws Exception {
        Vehicle truck = vehicle("AB123CD", 50000);
        plan(truck, "Filtro de aire", 30000, 40000);   // consumió 10.000 de 30.000 (33%)
        plan(truck, "Correa", 20000, 38000);           // consumió 12.000 de 20.000 (60%)

        FleetStatusRowDto row = service.getFleetStatus(1, 20, null, true).items().get(0);

        assertEquals("al_dia", row.status());
        assertEquals("Correa", row.nextMaintenance().name());
    }

    @Test
    void unVehiculoSinPlanesEstaAlDiaYSinProximoMantenimiento() throws Exception {
        vehicle("AB123CD", 50000);

        FleetStatusRowDto row = service.getFleetStatus(1, 20, null, true).items().get(0);

        assertEquals("al_dia", row.status());
        assertNull(row.nextMaintenance());
        assertEquals(100, row.healthScore());
    }

    @Test
    void lasAsignacionesDesactivadasNoCuentan() throws Exception {
        Vehicle truck = vehicle("AB123CD", 50000);
        plan(truck, "Cambio de aceite", 10000, 39000).setActive(false);

        assertEquals("al_dia", service.getFleetStatus(1, 20, null, true).items().get(0).status());
    }

    @Test
    void laTablaOrdenaPrimeroLosVencidosYFiltraPorEstado() throws Exception {
        Vehicle ok = vehicle("AA000AA", 10000);
        plan(ok, "Aceite", 10000, 9000);
        Vehicle overdue = vehicle("ZZ999ZZ", 50000);
        plan(overdue, "Aceite", 10000, 30000);

        PagedResponse<FleetStatusRowDto> all = service.getFleetStatus(1, 20, null, true);
        PagedResponse<FleetStatusRowDto> onlyOverdue = service.getFleetStatus(1, 20, "vencido", true);

        assertEquals(List.of("ZZ999ZZ", "AA000AA"), all.items().stream().map(FleetStatusRowDto::plate).toList());
        assertEquals(List.of("ZZ999ZZ"), onlyOverdue.items().stream().map(FleetStatusRowDto::plate).toList());
    }

    @Test
    void paginaYToleraParametrosFueraDeRango() throws Exception {
        for (int i = 0; i < 5; i++) {
            vehicle("AB10" + i + "CD", 1000);
        }

        PagedResponse<FleetStatusRowDto> second = service.getFleetStatus(2, 2, null, true);
        assertEquals(5, second.total());
        assertEquals(2, second.items().size());
        assertEquals(0, service.getFleetStatus(10, 2, null, true).items().size());
        // page=0 / pageSize=0 se toman como 1 en vez de explotar con un índice negativo.
        assertEquals(1, service.getFleetStatus(0, 0, null, true).items().size());
    }

    @Test
    void elListadoDeVehiculosVaOrdenadoPorPatenteSinImportarMayusculas() throws Exception {
        vehicle("cd456ef", 0);
        vehicle("AB123CD", 0);
        vehicle("Bc000aa", 0);

        List<VehicleSummaryDto> list = service.listVehicles(true);

        assertEquals(List.of("AB123CD", "Bc000aa", "cd456ef"), list.stream().map(VehicleSummaryDto::plate).toList());
    }

    // --- Kilometraje ---

    @Test
    void cargarKilometrajeLoActualiza() throws Exception {
        Vehicle truck = vehicle("AB123CD", 50000);

        OdometerResultDto result = service.updateOdometer(truck.getId().toString(), 50500);

        assertEquals(50500, result.odometerKm());
        assertEquals(50500, truck.getOdometerKm());
    }

    @Test
    void elKilometrajeNoPuedeRetroceder() throws Exception {
        Vehicle truck = vehicle("AB123CD", 50000);

        MaintenanceConflictException ex = assertThrows(MaintenanceConflictException.class,
                () -> service.updateOdometer(truck.getId().toString(), 49999));

        assertEquals("ODOMETER_REGRESSION", ex.getErrorCode());
        assertEquals(50000, truck.getOdometerKm());
    }

    @Test
    void noSeCargaKilometrajeAUnVehiculoDadoDeBaja() throws Exception {
        Vehicle truck = vehicle("AB123CD", 50000);
        truck.setActive(false);

        assertEquals("VEHICLE_INACTIVE", assertThrows(VehicleStateConflictException.class,
                () -> service.updateOdometer(truck.getId().toString(), 51000)).getErrorCode());
        verify(vehicleRepository, never()).save(any());
    }

    // --- Edición ---

    @Test
    void unVehiculoDadoDeBajaSoloSePuedeReactivar() throws Exception {
        Vehicle truck = vehicle("AB123CD", 50000);
        truck.setActive(false);

        assertEquals("VEHICLE_INACTIVE", assertThrows(VehicleStateConflictException.class,
                () -> service.update(truck.getId().toString(),
                        new UpdateVehicleRequest(null, "Iveco", null, null, null, null, true))).getErrorCode());

        VehicleSummaryDto reactivated = service.update(truck.getId().toString(),
                new UpdateVehicleRequest(null, null, null, null, null, null, true));
        assertTrue(reactivated.active());
    }

    @Test
    void noSePuedeCambiarLaPatenteAUnaQueYaUsaOtroVehiculo() throws Exception {
        Vehicle truck = vehicle("AB123CD", 50000);
        when(vehicleRepository.existsByPlateIgnoreCaseAndIdNot("CD456EF", truck.getId())).thenReturn(true);

        assertEquals("DUPLICATE_PLATE", assertThrows(VehicleStateConflictException.class,
                () -> service.update(truck.getId().toString(),
                        new UpdateVehicleRequest("CD456EF", null, null, null, null, null, null))).getErrorCode());
        assertEquals("AB123CD", truck.getPlate());
    }

    @Test
    void marcaYModeloNoPuedenQuedarVacios() throws Exception {
        Vehicle truck = vehicle("AB123CD", 50000);

        VehicleValidationException ex = assertThrows(VehicleValidationException.class,
                () -> service.update(truck.getId().toString(),
                        new UpdateVehicleRequest(null, " ", "", null, null, null, null)));

        assertEquals(2, ex.getDetails().size());
        assertEquals("Ford", truck.getBrand());
    }

}
