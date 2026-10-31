package org.fleetguard.service;

import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.entity.VehicleDocument;
import org.fleetguard.entity.VehicleDocumentType;
import org.fleetguard.event.VehicleDocumentChangedEvent;
import org.fleetguard.repository.VehicleDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.fleetguard.service.VehicleAvailabilityServiceTest.setId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CAM-127: renovar deja el anterior en el historial, y todo cambio propaga fechas, disponibilidad y avisos. */
class VehicleDocumentWriterTest {

    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");

    private final VehicleDocumentRepository repository = mock(VehicleDocumentRepository.class);
    private final LinkedDocumentDueDates linkedDueDates = mock(LinkedDocumentDueDates.class);
    private final VehicleAvailabilityService availabilityService = mock(VehicleAvailabilityService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final VehicleDocumentWriter writer = new VehicleDocumentWriter(repository, linkedDueDates, availabilityService,
            eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));

    private final UUID vehicleId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(repository.saveAndFlush(any(VehicleDocument.class))).thenAnswer(invocation -> {
            VehicleDocument d = invocation.getArgument(0);
            if (d.getId() == null) {
                setId(d, UUID.randomUUID());
            }
            return d;
        });
    }

    @Test
    void validaLosCamposConElPrefijoPedido() {
        List<FieldValidationErrorDetail> details = new ArrayList<>();

        VehicleDocumentWriter.Fields fields = VehicleDocumentWriter.parse("document.", "x".repeat(61), "2026-10-10",
                "2026-10-01", "  ", false, details);
        VehicleDocumentWriter.parse("", null, "ayer", null, null, true, details);

        assertEquals(List.of("document.number", "document.expiresAt", "issuedAt", "expiresAt"),
                details.stream().map(FieldValidationErrorDetail::field).toList());
        assertNull(fields.fileUrl());
        assertEquals(LocalDate.of(2026, 10, 10), fields.issuedAt());
    }

    @Test
    void renovarDejaElVigenteAnteriorEnElHistorialYPropagaElCambio() throws Exception {
        VehicleDocument previous = new VehicleDocument(vehicleId, VehicleDocumentType.VTV, null, "A-1", null,
                LocalDate.of(2026, 10, 12), null, NOW);
        setId(previous, UUID.randomUUID());
        when(repository.findFirstByVehicleIdAndTypeAndActiveTrueAndReplacedByIdIsNull(vehicleId, VehicleDocumentType.VTV))
                .thenReturn(Optional.of(previous));

        VehicleDocument saved = writer.renew(vehicleId, VehicleDocumentType.VTV, null,
                new VehicleDocumentWriter.Fields("B-2", LocalDate.of(2026, 10, 20), LocalDate.of(2027, 10, 20), null));

        assertEquals(saved.getId(), previous.getReplacedById());
        assertFalse(previous.isCurrent());
        assertTrue(saved.isCurrent());
        assertEquals(NOW, saved.getCreatedAt());
        verify(linkedDueDates).syncVehicle(vehicleId, VehicleDocumentType.VTV);
        verify(availabilityService).sync(vehicleId);
        verify(eventPublisher).publishEvent(new VehicleDocumentChangedEvent(vehicleId));
    }

    @Test
    void unDocumentoOtroNoReemplazaANingunoYRenovarUnoPuntualSiguePorEse() throws Exception {
        VehicleDocument saved = writer.renew(vehicleId, VehicleDocumentType.OTRO, "Permiso municipal",
                new VehicleDocumentWriter.Fields(null, null, null, null));
        verify(repository, never()).findFirstByVehicleIdAndTypeAndActiveTrueAndReplacedByIdIsNull(any(), any());

        VehicleDocument renewed = writer.renewOf(saved, new VehicleDocumentWriter.Fields(null, null, LocalDate.of(2027, 1, 1), null));

        assertEquals("Permiso municipal", renewed.getDisplayName());
        assertEquals(renewed.getId(), saved.getReplacedById());
    }
}
