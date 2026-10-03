package org.fleetguard.service;

import org.fleetguard.dto.CreateCompletionRequest;
import org.fleetguard.dto.CreateWorkOrderExpenseRequest;
import org.fleetguard.dto.CreateWorkOrderPhotoRequest;
import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.dto.UpdateScheduleRequest;
import org.fleetguard.dto.UpdateWorkOrderRequest;
import org.fleetguard.dto.WorkOrderDto;
import org.fleetguard.dto.WorkOrderExpenseDto;
import org.fleetguard.entity.Defect;
import org.fleetguard.entity.DefectSeverity;
import org.fleetguard.entity.ScheduleSourceType;
import org.fleetguard.entity.ScheduledMaintenance;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.WorkOrder;
import org.fleetguard.entity.WorkOrderExecutionType;
import org.fleetguard.entity.WorkOrderExpense;
import org.fleetguard.entity.WorkOrderExpenseCategory;
import org.fleetguard.entity.WorkOrderPhoto;
import org.fleetguard.entity.WorkOrderSourceType;
import org.fleetguard.entity.WorkOrderStatus;
import org.fleetguard.exception.WorkOrderConflictException;
import org.fleetguard.exception.WorkOrderNotFoundException;
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
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ciclo de vida de una orden de trabajo (CAM-14/CAM-15/CAM-62/CAM-63): asignada → en proceso →
 * finalizada, o cancelada antes de terminar. Finalizar es lo que "cierra el loop": registra el
 * mantenimiento hecho, cierra la programación o resuelve el defecto que le dio origen. El
 * vínculo con el técnico (CAM-60) está en WorkOrderServiceTest.
 */
class WorkOrderLifecycleServiceTest {

    private final WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
    private final WorkOrderExpenseRepository expenseRepository = mock(WorkOrderExpenseRepository.class);
    private final WorkOrderPhotoRepository photoRepository = mock(WorkOrderPhotoRepository.class);
    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final DefectRepository defectRepository = mock(DefectRepository.class);
    private final ScheduledMaintenanceRepository scheduleRepository = mock(ScheduledMaintenanceRepository.class);
    private final ScheduledMaintenanceService scheduledMaintenanceService = mock(ScheduledMaintenanceService.class);
    private final MaintenanceCompletionService completionService = mock(MaintenanceCompletionService.class);
    private final WorkOrderService service = new WorkOrderService(workOrderRepository, expenseRepository, photoRepository,
            vehicleRepository, defectRepository, scheduleRepository, scheduledMaintenanceService, completionService,
            mock(UserRepository.class), new WorkOrderMapper(), new DefectMapper());

    private final UUID vehicleId = UUID.randomUUID();
    private final Vehicle vehicle = new Vehicle("AB123CD", "Ford", "Cargo");

    private static void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    @BeforeEach
    void setUp() throws Exception {
        setId(vehicle, vehicleId);
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(expenseRepository.save(any(WorkOrderExpense.class))).thenAnswer(invocation -> {
            WorkOrderExpense expense = invocation.getArgument(0);
            setId(expense, UUID.randomUUID());
            return expense;
        });
        when(photoRepository.save(any(WorkOrderPhoto.class))).thenAnswer(invocation -> {
            WorkOrderPhoto photo = invocation.getArgument(0);
            setId(photo, UUID.randomUUID());
            return photo;
        });
    }

    private WorkOrder workOrder(UUID scheduleId, UUID defectId, UUID assignmentId) throws Exception {
        WorkOrder workOrder = new WorkOrder(vehicleId, WorkOrderSourceType.MANUAL, scheduleId, defectId, assignmentId,
                "Cambio de pastillas", null, WorkOrderExecutionType.INTERNO, null, null, null);
        setId(workOrder, UUID.randomUUID());
        when(workOrderRepository.findById(workOrder.getId())).thenReturn(Optional.of(workOrder));
        return workOrder;
    }

    private WorkOrder inProgress(UUID scheduleId, UUID defectId, UUID assignmentId) throws Exception {
        WorkOrder workOrder = workOrder(scheduleId, defectId, assignmentId);
        workOrder.start();
        return workOrder;
    }

    private void withPhotos(WorkOrder workOrder, long count) {
        when(photoRepository.countByWorkOrder_Id(workOrder.getId())).thenReturn(count);
    }

    private static UpdateWorkOrderRequest status(String status) {
        return new UpdateWorkOrderRequest(status, null, null, null, null, null, null, null);
    }

    private static UpdateWorkOrderRequest finalizeWith(String closingDescription, String completedKm) {
        return new UpdateWorkOrderRequest("finalizada", closingDescription,
                completedKm == null ? null : new BigDecimal(completedKm), null, null, null, null, null);
    }

    private static List<String> fields(WorkOrderValidationException ex) {
        return ex.getDetails().stream().map(FieldValidationErrorDetail::field).toList();
    }

    // --- Transiciones de estado ---

    @Test
    void unaOtAsignadaPasaAEnProcesoYDespuesAFinalizada() throws Exception {
        WorkOrder workOrder = workOrder(null, null, null);
        withPhotos(workOrder, 1);

        assertEquals("en_proceso", service.update(workOrder.getId().toString(), status("en_proceso")).status());
        WorkOrderDto finalized = service.update(workOrder.getId().toString(), finalizeWith("Se cambiaron las pastillas", null));

        assertEquals("finalizada", finalized.status());
        assertEquals("Se cambiaron las pastillas", workOrder.getClosingDescription());
        assertNotNull(workOrder.getFinalizedAt());
    }

    @Test
    void noSeFinalizaUnaOtQueNoEmpezo() throws Exception {
        WorkOrder workOrder = workOrder(null, null, null);
        withPhotos(workOrder, 1);

        WorkOrderConflictException ex = assertThrows(WorkOrderConflictException.class,
                () -> service.update(workOrder.getId().toString(), finalizeWith("Listo", null)));

        assertEquals("INVALID_STATUS_TRANSITION", ex.getErrorCode());
        assertEquals(WorkOrderStatus.ASIGNADA, workOrder.getStatus());
    }

    @Test
    void unaOtFinalizadaOCanceladaNoCambiaMasDeEstado() throws Exception {
        WorkOrder cancelled = workOrder(null, null, null);
        cancelled.cancel();
        WorkOrder finalized = inProgress(null, null, null);
        finalized.finalizeOrder("Hecho");

        assertEquals("WORK_ORDER_CLOSED", assertThrows(WorkOrderConflictException.class,
                () -> service.update(cancelled.getId().toString(), status("en_proceso"))).getErrorCode());
        assertEquals("WORK_ORDER_CLOSED", assertThrows(WorkOrderConflictException.class,
                () -> service.update(finalized.getId().toString(), status("cancelada"))).getErrorCode());
        // Volver a "asignada" no es una transición válida desde ningún estado.
        assertEquals("INVALID_STATUS_TRANSITION", assertThrows(WorkOrderConflictException.class,
                () -> service.update(inProgress(null, null, null).getId().toString(), status("asignada"))).getErrorCode());
    }

    @Test
    void unaOtFinalizadaOCanceladaNoSeEdita() throws Exception {
        WorkOrder cancelled = workOrder(null, null, null);
        cancelled.cancel();
        WorkOrder finalized = inProgress(null, null, null);
        finalized.finalizeOrder("Hecho");
        UpdateWorkOrderRequest edit = new UpdateWorkOrderRequest(null, null, null, null, null,
                "externo", "Taller Pérez", "Otra descripción");

        for (WorkOrder closed : List.of(cancelled, finalized)) {
            assertEquals("WORK_ORDER_CLOSED", assertThrows(WorkOrderConflictException.class,
                    () -> service.update(closed.getId().toString(), edit)).getErrorCode());
            // Queda como registro histórico: no se toca ningún campo ni se guarda nada.
            assertNull(closed.getDescription());
            assertEquals(WorkOrderExecutionType.INTERNO, closed.getExecutionType());
            assertNull(closed.getExternalProvider());
        }
        verify(workOrderRepository, never()).save(any());
    }

    @Test
    void unaOtSePuedeCancelarAsignadaOEnProceso() throws Exception {
        WorkOrder assigned = workOrder(null, null, null);
        WorkOrder started = inProgress(null, null, null);

        assertEquals("cancelada", service.update(assigned.getId().toString(), status("cancelada")).status());
        assertEquals("cancelada", service.update(started.getId().toString(), status("cancelada")).status());
    }

    @Test
    void unEstadoInexistenteDevuelve422() throws Exception {
        WorkOrder workOrder = workOrder(null, null, null);

        assertEquals(List.of("status"), fields(assertThrows(WorkOrderValidationException.class,
                () -> service.update(workOrder.getId().toString(), status("pausada")))));
    }

    // --- Finalizar: requisitos y cierre del origen ---

    @Test
    void finalizarExigeDescripcionDeCierreYAlMenosUnaFoto() throws Exception {
        WorkOrder workOrder = inProgress(null, null, null);
        withPhotos(workOrder, 0);

        WorkOrderValidationException ex = assertThrows(WorkOrderValidationException.class,
                () -> service.update(workOrder.getId().toString(), finalizeWith("  ", null)));

        assertEquals(List.of("closingDescription", "photos"), fields(ex));
        assertEquals(WorkOrderStatus.EN_PROCESO, workOrder.getStatus());
    }

    @Test
    void finalizarUnaOtDeUnPlanRegistraElMantenimientoHecho() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        WorkOrder workOrder = inProgress(UUID.randomUUID(), null, assignmentId);
        withPhotos(workOrder, 2);

        service.update(workOrder.getId().toString(), finalizeWith("Aceite y filtro cambiados", "61000"));

        ArgumentCaptor<CreateCompletionRequest> captor = ArgumentCaptor.forClass(CreateCompletionRequest.class);
        verify(completionService).create(eq(vehicleId.toString()), eq(assignmentId.toString()), captor.capture());
        assertEquals(LocalDate.now().toString(), captor.getValue().completedAt());
        assertEquals(61000L, captor.getValue().completedKm());
        // La completion queda vinculada a la OT que la generó (CAM-60).
        assertEquals(workOrder.getId().toString(), captor.getValue().workOrderId());
        // La programación de un plan la cierra MaintenanceCompletionService, no se cierra dos veces acá.
        verify(scheduledMaintenanceService, never()).update(any(), any());
    }

    @Test
    void finalizarRechazaUnKilometrajeNegativoConDecimalesOMenorAlDelVehiculo() throws Exception {
        vehicle.setOdometerKm(60000);
        WorkOrder workOrder = inProgress(UUID.randomUUID(), null, UUID.randomUUID());
        withPhotos(workOrder, 1);

        // Un decimal no se trunca: llega como BigDecimal y se rechaza, en vez de guardarse 60500.
        // "1e100000" es entero pero fuera de rango: se rechaza sin calcularlo (ver validateCompletedKm).
        for (String km : List.of("-1", "60500.5", "59999", "1e100000")) {
            WorkOrderValidationException ex = assertThrows(WorkOrderValidationException.class,
                    () -> service.update(workOrder.getId().toString(), finalizeWith("Aceite cambiado", km)));
            assertEquals(List.of("completedKm"), fields(ex));
        }
        assertEquals(WorkOrderStatus.EN_PROCESO, workOrder.getStatus());
        verify(completionService, never()).create(any(), any(), any());
    }

    @Test
    void finalizarAceptaElMismoKilometrajeQueYaTieneElVehiculo() throws Exception {
        vehicle.setOdometerKm(60000);
        UUID assignmentId = UUID.randomUUID();
        WorkOrder workOrder = inProgress(UUID.randomUUID(), null, assignmentId);
        withPhotos(workOrder, 1);

        service.update(workOrder.getId().toString(), finalizeWith("Aceite cambiado", "60000"));

        ArgumentCaptor<CreateCompletionRequest> captor = ArgumentCaptor.forClass(CreateCompletionRequest.class);
        verify(completionService).create(eq(vehicleId.toString()), eq(assignmentId.toString()), captor.capture());
        assertEquals(60000L, captor.getValue().completedKm());
    }

    @Test
    void finalizarUnaOtProgramadaAManoMarcaLaProgramacionComoRealizada() throws Exception {
        ScheduledMaintenance schedule = new ScheduledMaintenance(vehicleId, ScheduleSourceType.MANUAL, null, null,
                "Revisión eléctrica", Instant.now().plusSeconds(86400));
        setId(schedule, UUID.randomUUID());
        when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
        WorkOrder workOrder = inProgress(schedule.getId(), null, null);
        withPhotos(workOrder, 1);

        service.update(workOrder.getId().toString(), finalizeWith("Revisado", null));

        ArgumentCaptor<UpdateScheduleRequest> captor = ArgumentCaptor.forClass(UpdateScheduleRequest.class);
        verify(scheduledMaintenanceService).update(eq(schedule.getId().toString()), captor.capture());
        assertEquals("done", captor.getValue().status());
        verify(completionService, never()).create(any(), any(), any());
    }

    @Test
    void finalizarUnaOtDeUnDefectoLoResuelve() throws Exception {
        Defect defect = new Defect(DefectSeverity.BLOCKING, "Neumático cortado", "http://localhost/f.jpg", Instant.now());
        setId(defect, UUID.randomUUID());
        when(defectRepository.findById(defect.getId())).thenReturn(Optional.of(defect));
        WorkOrder workOrder = inProgress(null, defect.getId(), null);
        withPhotos(workOrder, 1);

        service.update(workOrder.getId().toString(), finalizeWith("Neumático reemplazado", null));

        // Un defecto resuelto deja de bloquear al vehículo (CAM-49).
        assertEquals("resuelto", defect.getStatus());
    }

    // --- Gastos y fotos ---

    @Test
    void cargarUnGastoEnUnaOtAbierta() throws Exception {
        WorkOrder workOrder = workOrder(null, null, null);

        WorkOrderExpenseDto expense = service.addExpense(workOrder.getId().toString(),
                new CreateWorkOrderExpenseRequest("repuesto", "Pastillas de freno", new BigDecimal("45000.50")));

        assertEquals("repuesto", expense.category());
        assertEquals(new BigDecimal("45000.50"), expense.amount());
    }

    @Test
    void unGastoNecesitaCategoriaValidaMontoPositivoYDescripcion() throws Exception {
        WorkOrder workOrder = workOrder(null, null, null);

        WorkOrderValidationException ex = assertThrows(WorkOrderValidationException.class,
                () -> service.addExpense(workOrder.getId().toString(),
                        new CreateWorkOrderExpenseRequest("combustible", " ", BigDecimal.ZERO)));

        assertEquals(List.of("category", "amount", "description"), fields(ex));
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void enUnaOtCerradaNoSeTocanGastosNiFotos() throws Exception {
        WorkOrder workOrder = workOrder(null, null, null);
        workOrder.cancel();
        String id = workOrder.getId().toString();

        assertEquals("WORK_ORDER_FINALIZED", assertThrows(WorkOrderConflictException.class, () -> service.addExpense(id,
                new CreateWorkOrderExpenseRequest("otro", "Grúa", BigDecimal.TEN))).getErrorCode());
        assertEquals("WORK_ORDER_FINALIZED", assertThrows(WorkOrderConflictException.class,
                () -> service.deleteExpense(id, UUID.randomUUID().toString())).getErrorCode());
        assertEquals("WORK_ORDER_CLOSED", assertThrows(WorkOrderConflictException.class,
                () -> service.addPhoto(id, new CreateWorkOrderPhotoRequest("http://localhost/f.jpg"))).getErrorCode());
        assertEquals("WORK_ORDER_CLOSED", assertThrows(WorkOrderConflictException.class,
                () -> service.deletePhoto(id, UUID.randomUUID().toString())).getErrorCode());
    }

    @Test
    void noSeBorraUnGastoNiUnaFotoDeOtraOt() throws Exception {
        WorkOrder mine = workOrder(null, null, null);
        WorkOrder other = workOrder(null, null, null);
        WorkOrderExpense foreignExpense = new WorkOrderExpense(other, WorkOrderExpenseCategory.OTRO, "Grúa", BigDecimal.TEN);
        setId(foreignExpense, UUID.randomUUID());
        when(expenseRepository.findById(foreignExpense.getId())).thenReturn(Optional.of(foreignExpense));
        WorkOrderPhoto foreignPhoto = new WorkOrderPhoto(other, "http://localhost/f.jpg");
        setId(foreignPhoto, UUID.randomUUID());
        when(photoRepository.findById(foreignPhoto.getId())).thenReturn(Optional.of(foreignPhoto));

        assertThrows(WorkOrderNotFoundException.class,
                () -> service.deleteExpense(mine.getId().toString(), foreignExpense.getId().toString()));
        assertThrows(WorkOrderNotFoundException.class,
                () -> service.deletePhoto(mine.getId().toString(), foreignPhoto.getId().toString()));
        verify(expenseRepository, never()).delete(any());
        verify(photoRepository, never()).delete(any());
    }

    @Test
    void borrarUnGastoYUnaFotoPropios() throws Exception {
        WorkOrder workOrder = workOrder(null, null, null);
        WorkOrderExpense expense = new WorkOrderExpense(workOrder, WorkOrderExpenseCategory.MANO_DE_OBRA, "Mano de obra", BigDecimal.TEN);
        setId(expense, UUID.randomUUID());
        when(expenseRepository.findById(expense.getId())).thenReturn(Optional.of(expense));
        WorkOrderPhoto photo = new WorkOrderPhoto(workOrder, "http://localhost/f.jpg");
        setId(photo, UUID.randomUUID());
        when(photoRepository.findById(photo.getId())).thenReturn(Optional.of(photo));

        service.deleteExpense(workOrder.getId().toString(), expense.getId().toString());
        service.deletePhoto(workOrder.getId().toString(), photo.getId().toString());

        verify(expenseRepository).delete(expense);
        verify(photoRepository).delete(photo);
    }

    @Test
    void agregarUnaFotoExigeLaUrl() throws Exception {
        WorkOrder workOrder = workOrder(null, null, null);

        assertThrows(WorkOrderValidationException.class,
                () -> service.addPhoto(workOrder.getId().toString(), new CreateWorkOrderPhotoRequest(" ")));
        assertEquals("http://localhost/f.jpg", service.addPhoto(workOrder.getId().toString(),
                new CreateWorkOrderPhotoRequest("http://localhost/f.jpg")).photoUrl());
    }

    // --- Ids y filtros ---

    @Test
    void idsInexistentesOMalFormadosDevuelven404() throws Exception {
        WorkOrder workOrder = workOrder(null, null, null);

        assertThrows(WorkOrderNotFoundException.class, () -> service.get(UUID.randomUUID().toString()));
        assertThrows(WorkOrderNotFoundException.class, () -> service.get("no-es-un-uuid"));
        assertThrows(WorkOrderNotFoundException.class,
                () -> service.deleteExpense(workOrder.getId().toString(), "no-es-un-uuid"));
        assertThrows(WorkOrderNotFoundException.class,
                () -> service.deletePhoto(workOrder.getId().toString(), "no-es-un-uuid"));
    }

    @Test
    void listarPorVehiculoYEstado() throws Exception {
        WorkOrder assigned = workOrder(null, null, null);
        WorkOrder started = inProgress(null, null, null);
        when(workOrderRepository.findByVehicleIdOrderByCreatedAtDesc(vehicleId)).thenReturn(List.of(started, assigned));

        List<WorkOrderDto> enProceso = service.list(vehicleId.toString(), "en_proceso", null, null);

        assertEquals(List.of(started.getId().toString()), enProceso.stream().map(WorkOrderDto::id).toList());
        assertEquals(2, service.list(vehicleId.toString(), null, "interno", null).size());
        // Un vehicleId mal formado en el filtro devuelve lista vacía, no 500.
        assertEquals(0, service.list("no-es-un-uuid", null, null, null).size());
    }
}
