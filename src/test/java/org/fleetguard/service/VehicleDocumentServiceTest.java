package org.fleetguard.service;

import org.fleetguard.dto.CreateCompletionRequest;
import org.fleetguard.dto.CreateVehicleDocumentRequest;
import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.dto.RenewVehicleDocumentRequest;
import org.fleetguard.dto.UpdateVehicleDocumentRequest;
import org.fleetguard.dto.VehicleDocumentDto;
import org.fleetguard.dto.VehicleDocumentsDto;
import org.fleetguard.entity.IntervalType;
import org.fleetguard.entity.MaintenanceCompletion;
import org.fleetguard.entity.MaintenancePlan;
import org.fleetguard.entity.NotificationChannel;
import org.fleetguard.entity.NotificationType;
import org.fleetguard.entity.Role;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.VehicleDocument;
import org.fleetguard.entity.VehicleDocumentType;
import org.fleetguard.entity.VehicleMaintenanceAssignment;
import org.fleetguard.exception.DocumentNotFoundException;
import org.fleetguard.exception.VehicleNotFoundException;
import org.fleetguard.exception.VehicleStateConflictException;
import org.fleetguard.exception.VehicleValidationException;
import org.fleetguard.repository.MaintenanceCompletionRepository;
import org.fleetguard.repository.VehicleDocumentRepository;
import org.fleetguard.repository.VehicleMaintenanceAssignmentRepository;
import org.fleetguard.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.fleetguard.service.VehicleAvailabilityServiceTest.setId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CAM-127: carga, renovación, corrección y baja de la documentación de un vehículo. */
class VehicleDocumentServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 29);
    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");

    private final VehicleDocumentRepository documentRepository = mock(VehicleDocumentRepository.class);
    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final VehicleMaintenanceAssignmentRepository assignmentRepository = mock(VehicleMaintenanceAssignmentRepository.class);
    private final MaintenanceCompletionRepository completionRepository = mock(MaintenanceCompletionRepository.class);
    private final MaintenanceCompletionService completionService = mock(MaintenanceCompletionService.class);
    private final VehicleDocumentWriter writer = mock(VehicleDocumentWriter.class);
    private final VehicleAvailabilityService availabilityService = mock(VehicleAvailabilityService.class);
    private final NotificationSettingsService settings = mock(NotificationSettingsService.class);
    private final VehicleDocumentService service = new VehicleDocumentService(documentRepository, vehicleRepository,
            assignmentRepository, completionRepository, completionService, writer, availabilityService, settings);

    private Vehicle vehicle;
    private String vid;

    private VehicleDocument doc(VehicleDocumentType type, LocalDate expiresAt) throws Exception {
        VehicleDocument d = new VehicleDocument(vehicle.getId(), type, null, "N-1", null, expiresAt, null, NOW);
        setId(d, UUID.randomUUID());
        return d;
    }

    private VehicleMaintenanceAssignment rtoAssignment() throws Exception {
        MaintenancePlan rto = new MaintenancePlan("Revisión técnica (RTO)", "documentacion", IntervalType.TIME, null, 365);
        rto.setDocumentType(VehicleDocumentType.VTV);
        VehicleMaintenanceAssignment a = new VehicleMaintenanceAssignment(vehicle.getId(), rto, null, LocalDate.of(2025, 10, 1));
        setId(a, UUID.randomUUID());
        return a;
    }

    @BeforeEach
    void setUp() throws Exception {
        vehicle = new Vehicle("AB123CD", "Iveco", "Daily");
        setId(vehicle, UUID.randomUUID());
        vid = vehicle.getId().toString();
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(availabilityService.today()).thenReturn(TODAY);
        when(settings.effective(NotificationType.DOCUMENT_DUE_SOON)).thenReturn(new EffectiveNotificationSetting(
                NotificationType.DOCUMENT_DUE_SOON, true, Set.of(NotificationChannel.IN_APP), Set.of(Role.ADMIN), Set.of(),
                15, null, false));
        when(writer.renew(any(), any(), any(), any())).thenAnswer(invocation -> {
            VehicleDocumentWriter.Fields f = invocation.getArgument(3);
            VehicleDocument d = new VehicleDocument(vehicle.getId(), invocation.getArgument(1), invocation.getArgument(2),
                    f.number(), f.issuedAt(), f.expiresAt(), f.fileUrl(), NOW);
            setId(d, UUID.randomUUID());
            return d;
        });
    }

    @Test
    void listaLosVigentesPorVencimientoElHistorialYLosObligatoriosQueFaltan() throws Exception {
        VehicleDocument title = doc(VehicleDocumentType.TITULO, null);
        VehicleDocument vtv = doc(VehicleDocumentType.VTV, TODAY.plusDays(10));
        VehicleDocument cedula = doc(VehicleDocumentType.CEDULA_AZUL, TODAY.minusDays(3));
        VehicleDocument oldVtv = doc(VehicleDocumentType.VTV, TODAY.minusDays(355));
        oldVtv.replaceWith(vtv.getId());
        when(documentRepository.findByVehicleIdAndActiveTrue(vehicle.getId())).thenReturn(List.of(title, vtv, cedula, oldVtv));

        VehicleDocumentsDto result = service.list(vid);

        assertEquals(List.of("cedula_azul", "vtv", "titulo"), result.items().stream().map(VehicleDocumentDto::type).toList());
        assertEquals(List.of("vencido", "por_vencer", "sin_vencimiento"), result.items().stream().map(VehicleDocumentDto::status).toList());
        assertEquals(-3L, result.items().get(0).daysToExpiry());
        assertEquals(1, result.history().size());
        assertFalse(result.history().get(0).current());
        assertEquals(vtv.getId().toString(), result.history().get(0).replacedById());
        assertEquals(List.of("seguro"), result.missingRequired());
        assertEquals(15, result.leadDays());
    }

    @Test
    void cargarUnDocumentoRegistraElMantenimientoDelPlanVinculado() throws Exception {
        VehicleMaintenanceAssignment rto = rtoAssignment();
        when(assignmentRepository.findByVehicleIdAndActive(vehicle.getId(), true)).thenReturn(List.of(rto));

        VehicleDocumentDto dto = service.create(vid, new CreateVehicleDocumentRequest("vtv", "ignorado", " 0012 ", "2026-10-20",
                "2027-10-20", "http://localhost:8080/api/photos/p1"));

        assertEquals("vtv", dto.type());
        assertNull(dto.name());
        assertEquals("0012", dto.number());
        assertEquals("al_dia", dto.status());
        ArgumentCaptor<CreateCompletionRequest> completion = ArgumentCaptor.forClass(CreateCompletionRequest.class);
        verify(completionService).create(eq(vid), eq(rto.getId().toString()), completion.capture());
        assertEquals("2026-10-20", completion.getValue().completedAt());
        assertNull(completion.getValue().document());
    }

    @Test
    void siYaHabiaUnMantenimientoPosteriorNoRegistraOtroYSinEmisionUsaHoy() throws Exception {
        VehicleMaintenanceAssignment rto = rtoAssignment();
        when(assignmentRepository.findByVehicleIdAndActive(vehicle.getId(), true)).thenReturn(List.of(rto));
        MaintenanceCompletion later = mock(MaintenanceCompletion.class);
        when(later.getCompletedAt()).thenReturn(LocalDate.of(2026, 10, 25));
        when(completionRepository.findFirstByAssignment_IdOrderByCompletedAtDesc(rto.getId())).thenReturn(Optional.of(later));

        service.create(vid, new CreateVehicleDocumentRequest("vtv", null, null, "2026-10-20", "2027-10-20", null));
        verify(completionService, never()).create(anyString(), anyString(), any());

        when(completionRepository.findFirstByAssignment_IdOrderByCompletedAtDesc(rto.getId())).thenReturn(Optional.empty());
        service.create(vid, new CreateVehicleDocumentRequest("vtv", null, null, null, "2027-10-20", null));
        ArgumentCaptor<CreateCompletionRequest> completion = ArgumentCaptor.forClass(CreateCompletionRequest.class);
        verify(completionService).create(eq(vid), anyString(), completion.capture());
        assertEquals(TODAY.toString(), completion.getValue().completedAt());
    }

    @Test
    void validaElTipoElNombreDeOtroYLasFechas() {
        VehicleValidationException ex = assertThrows(VehicleValidationException.class, () ->
                service.create(vid, new CreateVehicleDocumentRequest("licencia", null, null, null, null, null)));
        assertEquals("type", ex.getDetails().get(0).field());

        ex = assertThrows(VehicleValidationException.class, () ->
                service.create(vid, new CreateVehicleDocumentRequest("otro", " ", null, "2026-10-10", "2026-01-01", null)));
        assertEquals(List.of("name", "expiresAt"), ex.getDetails().stream().map(FieldValidationErrorDetail::field).toList());

        ex = assertThrows(VehicleValidationException.class, () ->
                service.create(vid, new CreateVehicleDocumentRequest("otro", "x".repeat(61), null, null, null, null)));
        assertEquals("name", ex.getDetails().get(0).field());
        verify(writer, never()).renew(any(), any(), any(), any());
    }

    @Test
    void unTipoYaCargadoSeRenuevaNoSeCargaDosVecesPeroOtroSePuedeRepetir() throws Exception {
        when(documentRepository.findFirstByVehicleIdAndTypeAndActiveTrueAndReplacedByIdIsNull(vehicle.getId(), VehicleDocumentType.SEGURO))
                .thenReturn(Optional.of(doc(VehicleDocumentType.SEGURO, TODAY.plusDays(100))));

        VehicleStateConflictException ex = assertThrows(VehicleStateConflictException.class, () ->
                service.create(vid, new CreateVehicleDocumentRequest("seguro", null, null, null, "2027-01-01", null)));
        assertEquals("DOCUMENT_ALREADY_LOADED", ex.getErrorCode());

        VehicleDocumentDto otro = service.create(vid, new CreateVehicleDocumentRequest("otro", "Permiso municipal", null, null, null, null));
        assertEquals("Permiso municipal", otro.displayName());
        assertEquals("sin_vencimiento", otro.status());
    }

    @Test
    void conUnVehiculoDadoDeBajaOInexistenteNoSeCarga() {
        vehicle.setActive(false);
        VehicleStateConflictException ex = assertThrows(VehicleStateConflictException.class, () ->
                service.create(vid, new CreateVehicleDocumentRequest("vtv", null, null, null, null, null)));
        assertEquals("VEHICLE_INACTIVE", ex.getErrorCode());
        assertThrows(VehicleNotFoundException.class, () -> service.list("no-es-un-id"));
    }

    @Test
    void renovarElVigenteRegistraElMantenimientoYUnoYaRenovadoNoSeRenueva() throws Exception {
        VehicleDocument vtv = doc(VehicleDocumentType.VTV, TODAY.minusDays(2));
        when(documentRepository.findByIdAndVehicleIdAndActiveTrue(vtv.getId(), vehicle.getId())).thenReturn(Optional.of(vtv));
        when(writer.renewOf(eq(vtv), any())).thenAnswer(invocation -> {
            VehicleDocumentWriter.Fields f = invocation.getArgument(1);
            VehicleDocument d = new VehicleDocument(vehicle.getId(), VehicleDocumentType.VTV, null, f.number(), f.issuedAt(),
                    f.expiresAt(), f.fileUrl(), NOW);
            setId(d, UUID.randomUUID());
            return d;
        });
        VehicleMaintenanceAssignment rto = rtoAssignment();
        when(assignmentRepository.findByVehicleIdAndActive(vehicle.getId(), true)).thenReturn(List.of(rto));

        VehicleDocumentDto renewed = service.renew(vid, vtv.getId().toString(),
                new RenewVehicleDocumentRequest("B-2", "2026-10-28", "2027-10-28", null));

        assertEquals("2027-10-28", renewed.expiresAt());
        verify(completionService).create(eq(vid), eq(rto.getId().toString()), any());

        vtv.replaceWith(UUID.randomUUID());
        VehicleStateConflictException ex = assertThrows(VehicleStateConflictException.class, () ->
                service.renew(vid, vtv.getId().toString(), new RenewVehicleDocumentRequest(null, null, "2028-01-01", null)));
        assertEquals("DOCUMENT_REPLACED", ex.getErrorCode());

        VehicleDocument seguro = doc(VehicleDocumentType.SEGURO, null);
        when(documentRepository.findByIdAndVehicleIdAndActiveTrue(seguro.getId(), vehicle.getId())).thenReturn(Optional.of(seguro));
        VehicleValidationException invalid = assertThrows(VehicleValidationException.class, () ->
                service.renew(vid, seguro.getId().toString(), new RenewVehicleDocumentRequest(null, null, "31/12/2027", null)));
        assertEquals("expiresAt", invalid.getDetails().get(0).field());
    }

    @Test
    void corregirCambiaSoloLoQueVinoYUnStringVacioBorra() throws Exception {
        VehicleDocument vtv = doc(VehicleDocumentType.VTV, TODAY.plusDays(5));
        vtv.setFileUrl("http://x/f.pdf");
        when(documentRepository.findByIdAndVehicleIdAndActiveTrue(vtv.getId(), vehicle.getId())).thenReturn(Optional.of(vtv));

        VehicleDocumentDto dto = service.update(vid, vtv.getId().toString(),
                new UpdateVehicleDocumentRequest(null, "", null, "2026-12-31", null));

        assertNull(dto.number());
        assertEquals("2026-12-31", dto.expiresAt());
        assertEquals("http://x/f.pdf", dto.fileUrl());
        verify(writer).afterChange(vehicle.getId(), VehicleDocumentType.VTV);

        VehicleValidationException ex = assertThrows(VehicleValidationException.class, () ->
                service.update(vid, vtv.getId().toString(), new UpdateVehicleDocumentRequest(null, null, null, "mañana", null)));
        assertEquals("expiresAt", ex.getDetails().get(0).field());
        assertThrows(DocumentNotFoundException.class, () ->
                service.update(vid, UUID.randomUUID().toString(), new UpdateVehicleDocumentRequest(null, null, null, null, null)));
    }

    @Test
    void borrarEsUnaBajaLogicaQueDejaDeBloquear() throws Exception {
        VehicleDocument vtv = doc(VehicleDocumentType.VTV, TODAY.minusDays(2));
        when(documentRepository.findByIdAndVehicleIdAndActiveTrue(vtv.getId(), vehicle.getId())).thenReturn(Optional.of(vtv));

        service.delete(vid, vtv.getId().toString());

        assertFalse(vtv.isActive());
        verify(documentRepository).save(vtv);
        verify(writer).afterChange(vehicle.getId(), VehicleDocumentType.VTV);
        assertThrows(DocumentNotFoundException.class, () -> service.delete(vid, "x"));
    }
}
