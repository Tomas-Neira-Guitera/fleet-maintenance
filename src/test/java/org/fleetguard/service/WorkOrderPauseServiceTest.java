package org.fleetguard.service;

import org.fleetguard.dto.UpdateWorkOrderRequest;
import org.fleetguard.dto.WorkOrderDto;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.WorkOrder;
import org.fleetguard.entity.WorkOrderExecutionType;
import org.fleetguard.entity.WorkOrderPause;
import org.fleetguard.entity.WorkOrderPauseReason;
import org.fleetguard.entity.WorkOrderSourceType;
import org.fleetguard.entity.WorkOrderStatus;
import org.fleetguard.exception.WorkOrderConflictException;
import org.fleetguard.exception.WorkOrderValidationException;
import org.fleetguard.mapper.DefectMapper;
import org.fleetguard.mapper.WorkOrderMapper;
import org.fleetguard.repository.DefectRepository;
import org.fleetguard.repository.ScheduledMaintenanceRepository;
import org.fleetguard.repository.UserRepository;
import org.fleetguard.repository.VehicleRepository;
import org.fleetguard.repository.WorkOrderExpenseRepository;
import org.fleetguard.repository.WorkOrderPhotoRepository;
import org.fleetguard.repository.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CAM-148: una OT en proceso se pone en espera con un motivo y se retoma. Mientras está en espera
 * sigue abierta (impide otra para el mismo origen, admite gastos y fotos), no se puede finalizar
 * sin retomarla y sí se puede cancelar. Cada pausa queda registrada y se suma el tiempo en espera.
 */
class WorkOrderPauseServiceTest {

    private final WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
    private final WorkOrderPhotoRepository photoRepository = mock(WorkOrderPhotoRepository.class);
    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final WorkOrderService service = new WorkOrderService(workOrderRepository,
            mock(WorkOrderExpenseRepository.class), photoRepository, vehicleRepository, mock(DefectRepository.class),
            mock(ScheduledMaintenanceRepository.class), mock(ScheduledMaintenanceService.class),
            mock(MaintenanceCompletionService.class), mock(UserRepository.class), new WorkOrderMapper(),
            new DefectMapper(), new OdometerReadings(5000), mock(ApplicationEventPublisher.class),
            mock(VehicleAvailabilityService.class));

    private final UUID vehicleId = UUID.randomUUID();
    private WorkOrder workOrder;

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @BeforeEach
    void setUp() throws Exception {
        Vehicle vehicle = new Vehicle("AB123CD", "Ford", "Cargo");
        setField(vehicle, "id", vehicleId);
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        workOrder = new WorkOrder(vehicleId, WorkOrderSourceType.MANUAL, null, null, null, "Cambio de pastillas", null,
                WorkOrderExecutionType.INTERNO, null, null, null);
        setField(workOrder, "id", UUID.randomUUID());
        when(workOrderRepository.findById(workOrder.getId())).thenReturn(Optional.of(workOrder));
    }

    private WorkOrderDto patch(String status, String reason, String comment) {
        return service.update(workOrder.getId().toString(),
                new UpdateWorkOrderRequest(status, null, null, null, null, null, null, null, null, reason, comment));
    }

    @Test
    void unaOtEnProcesoPasaAEnEsperaConElMotivo() {
        workOrder.start();

        WorkOrderDto dto = patch("en_espera", "esperando_repuesto", "  Pastillas pedidas a Bosch ");

        assertEquals("en_espera", dto.status());
        assertEquals("esperando_repuesto", dto.pause().reason());
        assertEquals("Pastillas pedidas a Bosch", dto.pause().comment());
        assertNotNull(dto.pause().since());
        assertEquals(1, workOrder.getPauses().size());
        assertNull(workOrder.getPauses().get(0).getEndedAt());
    }

    @Test
    void sinMotivoOConUnMotivoQueNoExisteNoSePausa() {
        workOrder.start();

        WorkOrderValidationException missing = assertThrows(WorkOrderValidationException.class,
                () -> patch("en_espera", null, null));
        WorkOrderValidationException unknown = assertThrows(WorkOrderValidationException.class,
                () -> patch("en_espera", "almuerzo", null));

        assertEquals("pauseReason", missing.getDetails().get(0).field());
        assertEquals("pauseReason", unknown.getDetails().get(0).field());
        assertEquals(WorkOrderStatus.EN_PROCESO, workOrder.getStatus());
    }

    @Test
    void conElMotivoOtroElComentarioEsObligatorio() {
        workOrder.start();

        WorkOrderValidationException error = assertThrows(WorkOrderValidationException.class,
                () -> patch("en_espera", "otro", "   "));

        assertEquals("pauseComment", error.getDetails().get(0).field());
        assertEquals("en_espera", patch("en_espera", "otro", "Falta la firma del seguro").status());
    }

    @Test
    void unComentarioDemasiadoLargoSeRechaza() {
        workOrder.start();

        assertThrows(WorkOrderValidationException.class,
                () -> patch("en_espera", "esperando_repuesto", "x".repeat(WorkOrderService.PAUSE_COMMENT_MAX_LENGTH + 1)));
    }

    @Test
    void soloUnaOtEnProcesoSePuedePausar() throws Exception {
        // Asignada.
        assertThrows(WorkOrderConflictException.class, () -> patch("en_espera", "esperando_repuesto", null));
        // Finalizada y cancelada.
        workOrder.start();
        workOrder.finalizeOrder("Listo");
        assertThrows(WorkOrderConflictException.class, () -> patch("en_espera", "esperando_repuesto", null));
        setField(workOrder, "status", WorkOrderStatus.CANCELADA);
        assertThrows(WorkOrderConflictException.class, () -> patch("en_espera", "esperando_repuesto", null));
    }

    @Test
    void unaOtEnEsperaNoSeFinalizaSinRetomarla() {
        workOrder.start();
        patch("en_espera", "esperando_proveedor", null);
        when(photoRepository.countByWorkOrder_Id(workOrder.getId())).thenReturn(1L);

        WorkOrderConflictException error = assertThrows(WorkOrderConflictException.class, () ->
                service.update(workOrder.getId().toString(),
                        new UpdateWorkOrderRequest("finalizada", "Listo", null, null, null, null, null, null, null)));

        assertEquals("INVALID_STATUS_TRANSITION", error.getErrorCode());
        assertTrue(error.getMessage().contains("retomala"));
    }

    @Test
    void alRetomarlaSeCierraLaPausaYSeSumaElTiempo() throws Exception {
        workOrder.start();
        patch("en_espera", "esperando_repuesto", null);
        // La pausa empezó hace 2 horas.
        Instant twoHoursAgo = Instant.now().minus(Duration.ofHours(2));
        setField(workOrder, "pausedAt", twoHoursAgo);
        setField(workOrder.getPauses().get(0), "startedAt", twoHoursAgo);

        WorkOrderDto dto = patch("en_proceso", null, null);

        assertEquals("en_proceso", dto.status());
        assertNull(dto.pause());
        assertTrue(Math.abs(dto.pausedSeconds() - 7200) <= 2);
        WorkOrderPause pause = workOrder.getPauses().get(0);
        assertNotNull(pause.getEndedAt());
        assertEquals(WorkOrderPauseReason.ESPERANDO_REPUESTO, pause.getReason());
    }

    @Test
    void dosPausasQuedanRegistradasYSeSumanYCadaRetomarCierraSoloLaAbierta() throws Exception {
        workOrder.start();
        patch("en_espera", "esperando_repuesto", null);
        Instant firstStart = Instant.now().minus(Duration.ofHours(3));
        setField(workOrder, "pausedAt", firstStart);
        setField(workOrder.getPauses().get(0), "startedAt", firstStart);
        patch("en_proceso", null, null);   // la primera, 3 h
        Instant firstEnd = workOrder.getPauses().get(0).getEndedAt();

        patch("en_espera", "esperando_proveedor", null);
        Instant secondStart = Instant.now().minus(Duration.ofHours(1));
        setField(workOrder, "pausedAt", secondStart);
        setField(workOrder.getPauses().get(1), "startedAt", secondStart);
        WorkOrderDto dto = patch("en_proceso", null, null);   // la segunda, 1 h

        assertEquals(2, workOrder.getPauses().size());
        assertEquals(firstEnd, workOrder.getPauses().get(0).getEndedAt());
        assertNotNull(workOrder.getPauses().get(1).getEndedAt());
        assertEquals(WorkOrderPauseReason.ESPERANDO_PROVEEDOR, workOrder.getPauses().get(1).getReason());
        assertTrue(Math.abs(dto.pausedSeconds() - 4 * 3600) <= 2);
    }

    @Test
    void elTiempoEnEsperaSumaLasPausasYCuentaLaActualHastaAhora() throws Exception {
        setField(workOrder, "pausedSeconds", 3600L);
        workOrder.start();
        patch("en_espera", "esperando_vehiculo", null);
        setField(workOrder, "pausedAt", Instant.now().minus(Duration.ofMinutes(30)));

        long seconds = workOrder.pausedSecondsUntil(Instant.now());

        assertTrue(Math.abs(seconds - 5400) <= 2);
    }

    @Test
    void unaOtEnEsperaSePuedeCancelarYLaPausaSeCierra() {
        workOrder.start();
        patch("en_espera", "esperando_repuesto", null);

        WorkOrderDto dto = patch("cancelada", null, null);

        assertEquals("cancelada", dto.status());
        assertNull(dto.pause());
        assertNotNull(workOrder.getPauses().get(0).getEndedAt());
    }

    @Test
    void unaOtEnEsperaSigueAbierta() {
        workOrder.start();
        patch("en_espera", "esperando_repuesto", null);

        assertTrue(workOrder.isOpen());
        assertTrue(WorkOrderStatus.OPEN.contains(WorkOrderStatus.EN_ESPERA));
        assertTrue(WorkOrderStatus.EN_ESPERA.isStarted());
    }

    @Test
    void retomarUnaAsignadaLaEmpiezaComoSiempre() {
        assertEquals("en_proceso", patch("en_proceso", null, null).status());
        assertEquals(0, workOrder.getPauses().size());
    }
}
