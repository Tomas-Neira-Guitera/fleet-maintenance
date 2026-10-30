package org.fleetguard.service;

import org.fleetguard.dto.DowntimeDto;
import org.fleetguard.entity.CheckOutcome;
import org.fleetguard.entity.Defect;
import org.fleetguard.entity.DefectSeverity;
import org.fleetguard.entity.Inspection;
import org.fleetguard.entity.InspectionAnswer;
import org.fleetguard.entity.InspectionType;
import org.fleetguard.entity.Trip;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.WorkOrder;
import org.fleetguard.entity.WorkOrderExecutionType;
import org.fleetguard.entity.WorkOrderSourceType;
import org.fleetguard.exception.VehicleNotFoundException;
import org.fleetguard.exception.WorkOrderValidationException;
import org.fleetguard.repository.DefectRepository;
import org.fleetguard.repository.VehicleRepository;
import org.fleetguard.repository.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CAM-134 -- tiempo fuera de servicio (intervalos con al menos un bloqueante abierto, sin contar dos veces los
 * solapados) y tiempo promedio de reparación. Período fijo: la semana del lunes 05/10 al lunes 12/10, con "ahora"
 * después, salvo en los tests que prueban el corte en el momento actual.
 */
class DowntimeServiceTest {

    private static final String FROM = "2026-10-05T00:00:00Z";
    private static final String TO = "2026-10-12T00:00:00Z";
    private static final Instant NOW = Instant.parse("2026-10-29T15:00:00Z");
    private static final double WEEK_HOURS = 168;

    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final DefectRepository defectRepository = mock(DefectRepository.class);
    private final WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
    private DowntimeService service = new DowntimeService(vehicleRepository, defectRepository, workOrderRepository,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private Vehicle vehicle;
    private Vehicle otherVehicle;

    private static void set(Object entity, String fieldName, Object value) throws Exception {
        Field field = entity.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(entity, value);
    }

    private static Defect blocking(Vehicle vehicle, String reportedAt) throws Exception {
        Instant at = Instant.parse(reportedAt);
        Trip trip = new Trip(vehicle, at, UUID.randomUUID(), "chofer1");
        Inspection inspection = new Inspection(trip, vehicle.getId(), UUID.randomUUID().toString(), "chofer1",
                InspectionType.PRE_TRIP, at, null, null, true);
        InspectionAnswer answer = new InspectionAnswer("frenos", "Frenos", CheckOutcome.DEFECT, null);
        inspection.addAnswer(answer);
        Defect defect = new Defect(DefectSeverity.BLOCKING, "Frenos largos", "http://localhost/foto.jpg", at);
        set(defect, "id", UUID.randomUUID());
        answer.attachDefect(defect);
        return defect;
    }

    private static Defect resolved(Defect defect, String resolvedAt) {
        defect.resolve(Instant.parse(resolvedAt));
        return defect;
    }

    private static WorkOrder finalized(Vehicle vehicle, WorkOrderExecutionType type, String createdAt,
                                       String finalizedAt, UUID defectId) throws Exception {
        WorkOrder workOrder = new WorkOrder(vehicle.getId(), WorkOrderSourceType.MANUAL, null, defectId, null,
                "Reparación", null, type, null, null, null);
        workOrder.finalizeOrder("Hecho");
        set(workOrder, "createdAt", Instant.parse(createdAt));
        set(workOrder, "finalizedAt", Instant.parse(finalizedAt));
        return workOrder;
    }

    @BeforeEach
    void setUp() throws Exception {
        vehicle = new Vehicle("AB123CD", "Ford", "Cargo");
        set(vehicle, "id", UUID.randomUUID());
        otherVehicle = new Vehicle("AC456EF", "Fiat", "Ducato");
        set(otherVehicle, "id", UUID.randomUUID());
        when(vehicleRepository.findByActive(true)).thenReturn(List.of(vehicle, otherVehicle));
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(defectRepository.findBlockingInPeriodWithInspection(any(), any())).thenReturn(List.of());
        when(workOrderRepository.findFinalizedBetween(any(), any())).thenReturn(List.of());
        when(workOrderRepository.findFinalizedByDefectIds(any())).thenReturn(List.of());
    }

    private DowntimeDto.Vehicle row(DowntimeDto result, Vehicle of) {
        return result.vehicles().stream().filter(v -> v.vehicleId().equals(of.getId().toString())).findFirst().orElseThrow();
    }

    // --- fuera de servicio ---

    @Test
    void unBloqueanteDelLunes8AlMiercoles8SumaDosDias() throws Exception {
        when(defectRepository.findBlockingInPeriodWithInspection(Instant.parse(FROM), Instant.parse(TO)))
                .thenReturn(List.of(resolved(blocking(vehicle, "2026-10-05T08:00:00Z"), "2026-10-07T08:00:00Z")));

        DowntimeDto result = service.get(FROM, TO, null);

        DowntimeDto.Vehicle row = row(result, vehicle);
        assertEquals(48.0, row.downtimeHours());
        assertEquals(1, row.stops());
        assertEquals(71.4, row.availabilityPct());
        // La flota suma los dos vehículos: 48 h sobre 2 × 168 h.
        assertEquals(85.7, result.fleet().availabilityPct());
        assertEquals(48.0, result.fleet().downtimeHours());
        assertEquals(1, result.fleet().stops());
    }

    @Test
    void dosBloqueantesSolapadosNoDuplicanElTiempoYSonUnaSolaParada() throws Exception {
        when(defectRepository.findBlockingInPeriodWithInspection(any(), any())).thenReturn(List.of(
                resolved(blocking(vehicle, "2026-10-05T08:00:00Z"), "2026-10-07T08:00:00Z"),
                resolved(blocking(vehicle, "2026-10-06T08:00:00Z"), "2026-10-08T08:00:00Z")));

        DowntimeDto.Vehicle row = row(service.get(FROM, TO, null), vehicle);

        assertEquals(72.0, row.downtimeHours());
        assertEquals(1, row.stops());
    }

    @Test
    void dosParadasSeparadasCuentanDosVeces() throws Exception {
        when(defectRepository.findBlockingInPeriodWithInspection(any(), any())).thenReturn(List.of(
                resolved(blocking(vehicle, "2026-10-05T08:00:00Z"), "2026-10-05T20:00:00Z"),
                resolved(blocking(vehicle, "2026-10-09T08:00:00Z"), "2026-10-09T14:00:00Z")));

        DowntimeDto.Vehicle row = row(service.get(FROM, TO, null), vehicle);

        assertEquals(18.0, row.downtimeHours());
        assertEquals(2, row.stops());
    }

    @Test
    void unaParadaQueEmpezoAntesDelPeriodoCuentaSoloLaParteDeAdentro() throws Exception {
        when(defectRepository.findBlockingInPeriodWithInspection(any(), any())).thenReturn(List.of(
                resolved(blocking(vehicle, "2026-10-01T00:00:00Z"), "2026-10-06T00:00:00Z"),
                resolved(blocking(vehicle, "2026-09-01T00:00:00Z"), "2026-09-02T00:00:00Z")));

        DowntimeDto.Vehicle row = row(service.get(FROM, TO, null), vehicle);

        // La de septiembre no toca el período: no suma ni cuenta como parada.
        assertEquals(24.0, row.downtimeHours());
        assertEquals(1, row.stops());
    }

    @Test
    void unBloqueanteAbiertoCuentaHastaElFinDelPeriodo() throws Exception {
        when(defectRepository.findBlockingInPeriodWithInspection(any(), any()))
                .thenReturn(List.of(blocking(vehicle, "2026-10-11T00:00:00Z")));

        assertEquals(24.0, row(service.get(FROM, TO, null), vehicle).downtimeHours());
    }

    @Test
    void elPeriodoSeCortaEnElMomentoActual() throws Exception {
        // "Este mes" el 29/10 a las 15:00: mide del 01/10 a ahora, y un bloqueante abierto cuenta hasta ahora.
        when(defectRepository.findBlockingInPeriodWithInspection(any(), eq(NOW)))
                .thenReturn(List.of(blocking(vehicle, "2026-10-29T03:00:00Z")));

        DowntimeDto result = service.get("2026-10-01T00:00:00Z", "2026-11-01T00:00:00Z", vehicle.getId().toString());

        assertEquals(NOW, result.to());
        assertEquals(12.0, result.fleet().downtimeHours());
        // 12 h sobre 687 h (28 días y 15 horas).
        assertEquals(98.3, result.fleet().availabilityPct());
    }

    @Test
    void unPeriodoQueTodaviaNoEmpezoNoTieneDisponibilidad() {
        DowntimeDto result = service.get("2026-11-01T00:00:00Z", "2026-12-01T00:00:00Z", null);

        assertNull(result.fleet().availabilityPct());
        assertEquals(0.0, result.fleet().downtimeHours());
    }

    @Test
    void sinParadasLaFlotaEstaCienPorCientoDisponible() {
        DowntimeDto result = service.get(FROM, TO, null);

        assertEquals(100.0, result.fleet().availabilityPct());
        assertEquals(0, result.fleet().stops());
        assertEquals(2, result.vehicles().size());
    }

    @Test
    void unDefectoResueltoAntesDeCam134UsaLaFinalizacionDeSuOt() throws Exception {
        Defect legacy = blocking(vehicle, "2026-10-05T08:00:00Z");
        set(legacy, "status", "resuelto");
        when(defectRepository.findBlockingInPeriodWithInspection(any(), any())).thenReturn(List.of(legacy));
        when(workOrderRepository.findFinalizedByDefectIds(List.of(legacy.getId()))).thenReturn(List.of(
                finalized(vehicle, WorkOrderExecutionType.INTERNO, "2026-10-05T09:00:00Z", "2026-10-06T08:00:00Z", legacy.getId())));

        assertEquals(24.0, row(service.get(FROM, TO, null), vehicle).downtimeHours());
    }

    @Test
    void unDefectoResueltoSinFechaNiOtNoSumaTiempo() throws Exception {
        Defect legacy = blocking(vehicle, "2026-10-05T08:00:00Z");
        set(legacy, "status", "resuelto");
        when(defectRepository.findBlockingInPeriodWithInspection(any(), any())).thenReturn(List.of(legacy));

        DowntimeDto.Vehicle row = row(service.get(FROM, TO, null), vehicle);

        assertEquals(0.0, row.downtimeHours());
        assertEquals(0, row.stops());
    }

    @Test
    void ordenaDeMasTiempoFueraDeServicioAMenos() throws Exception {
        when(defectRepository.findBlockingInPeriodWithInspection(any(), any()))
                .thenReturn(List.of(resolved(blocking(otherVehicle, "2026-10-05T08:00:00Z"), "2026-10-05T10:00:00Z")));

        DowntimeDto result = service.get(FROM, TO, null);

        assertEquals("AC456EF", result.vehicles().get(0).plate());
        assertEquals("AB123CD", result.vehicles().get(1).plate());
    }

    // --- reparaciones ---

    @Test
    void elTiempoPromedioDeReparacionEsElPromedioDeLasOtsFinalizadasPorTipo() throws Exception {
        when(workOrderRepository.findFinalizedBetween(Instant.parse(FROM), Instant.parse(TO))).thenReturn(List.of(
                finalized(vehicle, WorkOrderExecutionType.INTERNO, "2026-10-05T08:00:00Z", "2026-10-05T18:00:00Z", null),
                finalized(vehicle, WorkOrderExecutionType.INTERNO, "2026-10-06T08:00:00Z", "2026-10-06T14:00:00Z", null),
                finalized(otherVehicle, WorkOrderExecutionType.EXTERNO, "2026-10-01T08:00:00Z", "2026-10-07T08:00:00Z", null)));

        DowntimeDto result = service.get(FROM, TO, null);

        // (10 + 6 + 144) / 3 = 53,3 h; internas (10 + 6) / 2 = 8; externas 144.
        assertEquals(53.3, result.fleet().repairs().avgHours());
        assertEquals(3, result.fleet().repairs().count());
        assertEquals(8.0, result.fleet().repairs().internal().avgHours());
        assertEquals(2, result.fleet().repairs().internal().count());
        assertEquals(144.0, result.fleet().repairs().external().avgHours());
        assertEquals(8.0, row(result, vehicle).repairs().avgHours());
        assertNull(row(result, vehicle).repairs().external().avgHours());
    }

    @Test
    void sinOtsFinalizadasElPromedioVieneEnNull() {
        DowntimeDto result = service.get(FROM, TO, null);

        assertNull(result.fleet().repairs().avgHours());
        assertEquals(0, result.fleet().repairs().count());
    }

    // --- un vehículo y validaciones ---

    @Test
    void conVehicleIdDevuelveSoloEseVehiculoAunqueEsteDadoDeBaja() throws Exception {
        vehicle.setActive(false);

        DowntimeDto result = service.get(FROM, TO, vehicle.getId().toString());

        assertEquals(1, result.vehicles().size());
        assertEquals("AB123CD", result.vehicles().get(0).plate());
        verify(vehicleRepository, never()).findByActive(true);
    }

    @Test
    void unVehiculoQueNoExisteOMalFormadoDevuelve404() {
        assertThrows(VehicleNotFoundException.class, () -> service.get(FROM, TO, UUID.randomUUID().toString()));
        assertThrows(VehicleNotFoundException.class, () -> service.get(FROM, TO, "abc"));
    }

    @Test
    void sinPeriodoOConElPeriodoAlRevesDevuelve422() {
        WorkOrderValidationException missing = assertThrows(WorkOrderValidationException.class,
                () -> service.get(null, " ", null));
        assertEquals(2, missing.getDetails().size());

        WorkOrderValidationException reversed = assertThrows(WorkOrderValidationException.class,
                () -> service.get(TO, FROM, null));
        assertEquals("to", reversed.getDetails().get(0).field());

        assertThrows(WorkOrderValidationException.class, () -> service.get("ayer", TO, null));
    }

    @Test
    void unaSegundaResolucionDelMismoDefectoNoCambiaSuFecha() throws Exception {
        Defect defect = resolved(blocking(vehicle, "2026-10-05T08:00:00Z"), "2026-10-06T08:00:00Z");

        // Otra OT del mismo defecto (una programación vieja) que se finaliza después.
        defect.resolve(Instant.parse("2026-10-09T08:00:00Z"));

        assertEquals(Instant.parse("2026-10-06T08:00:00Z"), defect.getResolvedAt());
    }
}
