package org.fleetguard.service;

import org.fleetguard.entity.IntervalType;
import org.fleetguard.entity.MaintenancePlan;
import org.fleetguard.entity.VehicleDocument;
import org.fleetguard.entity.VehicleDocumentType;
import org.fleetguard.entity.VehicleMaintenanceAssignment;
import org.fleetguard.repository.VehicleDocumentRepository;
import org.fleetguard.repository.VehicleMaintenanceAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.fleetguard.service.VehicleAvailabilityServiceTest.setId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CAM-127: un plan vinculado vence con el documento cargado; sin documento, con su intervalo. */
class LinkedDocumentDueDatesTest {

    private final VehicleDocumentRepository documentRepository = mock(VehicleDocumentRepository.class);
    private final VehicleMaintenanceAssignmentRepository assignmentRepository = mock(VehicleMaintenanceAssignmentRepository.class);
    private final LinkedDocumentDueDates linked = new LinkedDocumentDueDates(documentRepository, assignmentRepository);

    private final UUID vehicleId = UUID.randomUUID();
    private MaintenancePlan rto;
    private MaintenancePlan oil;

    private VehicleMaintenanceAssignment assignment(MaintenancePlan plan) {
        return new VehicleMaintenanceAssignment(vehicleId, plan, 50000L, LocalDate.of(2026, 1, 10));
    }

    private void vtvLoaded(LocalDate expiresAt) {
        when(documentRepository.findFirstByVehicleIdAndTypeAndActiveTrueAndReplacedByIdIsNull(vehicleId, VehicleDocumentType.VTV))
                .thenReturn(Optional.of(new VehicleDocument(vehicleId, VehicleDocumentType.VTV, null, null, null, expiresAt,
                        null, Instant.now())));
    }

    @BeforeEach
    void setUp() throws Exception {
        rto = new MaintenancePlan("Revisión técnica (RTO)", "documentacion", IntervalType.TIME, null, 365);
        rto.setDocumentType(VehicleDocumentType.VTV);
        setId(rto, UUID.randomUUID());
        oil = new MaintenancePlan("Aceite", "motor", IntervalType.KM, 10000, null);
        setId(oil, UUID.randomUUID());
    }

    @Test
    void conElDocumentoCargadoVenceCuandoVenceElDocumento() {
        vtvLoaded(LocalDate.of(2026, 12, 3));
        VehicleMaintenanceAssignment a = assignment(rto);

        linked.recalculate(a);

        assertEquals(LocalDate.of(2026, 12, 3), a.getNextDueDate());
    }

    @Test
    void sinDocumentoOSinVinculoRigeElIntervaloDelPlan() {
        VehicleMaintenanceAssignment a = assignment(rto);
        linked.recalculate(a);
        assertEquals(LocalDate.of(2027, 1, 10), a.getNextDueDate());

        VehicleMaintenanceAssignment b = assignment(oil);
        linked.recalculate(b);
        assertEquals(60000L, b.getNextDueKm());
        assertTrue(linked.documentDueDate(b).isEmpty());
    }

    @Test
    void sincronizaSoloLasAsignacionesVinculadasAEseTipo() {
        vtvLoaded(LocalDate.of(2026, 12, 3));
        VehicleMaintenanceAssignment a = assignment(rto);
        VehicleMaintenanceAssignment b = assignment(oil);
        when(assignmentRepository.findByVehicleIdAndActive(vehicleId, true)).thenReturn(List.of(a, b));

        linked.syncVehicle(vehicleId, VehicleDocumentType.VTV);

        assertEquals(LocalDate.of(2026, 12, 3), a.getNextDueDate());
        verify(assignmentRepository).saveAll(List.of(a));
    }

    @Test
    void alVincularElPlanSeRecalculanSusAsignaciones() {
        vtvLoaded(LocalDate.of(2026, 12, 3));
        VehicleMaintenanceAssignment a = assignment(rto);
        when(assignmentRepository.findByMaintenancePlan_IdAndActiveTrue(rto.getId())).thenReturn(List.of(a));

        linked.syncPlan(rto);

        assertEquals(LocalDate.of(2026, 12, 3), a.getNextDueDate());
        verify(assignmentRepository).saveAll(List.of(a));
    }
}
