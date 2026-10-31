package org.fleetguard.service;

import org.fleetguard.dto.BlockReasonDto;
import org.fleetguard.entity.CheckOutcome;
import org.fleetguard.entity.Defect;
import org.fleetguard.entity.DefectSeverity;
import org.fleetguard.entity.Inspection;
import org.fleetguard.entity.InspectionAnswer;
import org.fleetguard.entity.InspectionType;
import org.fleetguard.entity.Trip;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.VehicleDocument;
import org.fleetguard.entity.VehicleDocumentType;
import org.fleetguard.event.VehicleAvailabilityChangedEvent;
import org.fleetguard.repository.DefectRepository;
import org.fleetguard.repository.VehicleDocumentRepository;
import org.fleetguard.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CAM-127 / CAM-102 / CAM-103: qué deja a un vehículo sin poder salir y cuándo cambia. */
class VehicleAvailabilityServiceTest {

    // 12:00 UTC = 09:00 en Argentina: "hoy" es el 29/10.
    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 29);

    private final DefectRepository defectRepository = mock(DefectRepository.class);
    private final VehicleDocumentRepository documentRepository = mock(VehicleDocumentRepository.class);
    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final VehicleAvailabilityService service = new VehicleAvailabilityService(defectRepository, documentRepository,
            vehicleRepository, eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));

    private Vehicle vehicle;

    static void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    static Defect blockingDefect(Vehicle vehicle, String title, Instant reportedAt) {
        Inspection inspection = new Inspection(new Trip(vehicle, reportedAt), vehicle.getId(), UUID.randomUUID().toString(),
                "chofer1", InspectionType.PRE_TRIP, reportedAt, 1000.0, null, true);
        InspectionAnswer answer = new InspectionAnswer("ext-luces", "Luces", CheckOutcome.DEFECT, null);
        inspection.addAnswer(answer);
        Defect defect = new Defect(DefectSeverity.BLOCKING, title, null, reportedAt);
        answer.attachDefect(defect);
        return defect;
    }

    static VehicleDocument document(Vehicle vehicle, VehicleDocumentType type, LocalDate expiresAt) throws Exception {
        VehicleDocument document = new VehicleDocument(vehicle.getId(), type, null, null, null, expiresAt, null, NOW);
        setId(document, UUID.randomUUID());
        return document;
    }

    @BeforeEach
    void setUp() throws Exception {
        vehicle = new Vehicle("AB123CD", "Iveco", "Daily");
        setId(vehicle, UUID.randomUUID());
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
    }

    @Test
    void hoyEsElDiaSiguienteAlVencimientoCuentaComoVencido() {
        assertFalse(VehicleAvailabilityService.isExpired(TODAY, TODAY));
        assertTrue(VehicleAvailabilityService.isExpired(TODAY.minusDays(1), TODAY));
        assertFalse(VehicleAvailabilityService.isExpired(null, TODAY));
        assertEquals(TODAY, service.today());
    }

    @Test
    void juntaDefectosBloqueantesYDocumentosVencidosPorVehiculo() throws Exception {
        Vehicle other = new Vehicle("AC456EF", "Ford", "Cargo");
        setId(other, UUID.randomUUID());
        when(defectRepository.findOpenBlockingWithInspection(any()))
                .thenReturn(List.of(blockingDefect(vehicle, "Sin frenos", NOW)));
        when(documentRepository.findExpiredCurrent(any(), eq(TODAY))).thenReturn(List.of(
                document(vehicle, VehicleDocumentType.VTV, LocalDate.of(2026, 10, 12)),
                document(other, VehicleDocumentType.SEGURO, LocalDate.of(2026, 10, 1))));

        Map<UUID, List<BlockReasonDto>> reasons = service.reasonsFor(List.of(vehicle.getId(), other.getId()));

        assertEquals(List.of(
                new BlockReasonDto("blocking_defect", "Defecto bloqueante: Sin frenos"),
                new BlockReasonDto("expired_document", "VTV / RTO: venció el 12/10/2026")), reasons.get(vehicle.getId()));
        assertEquals(List.of(new BlockReasonDto("expired_document", "Seguro: venció el 01/10/2026")), reasons.get(other.getId()));
        assertEquals(Map.of(), service.reasonsFor(List.of()));
    }

    @Test
    void elMensajeDelRechazoNombraLaPatenteYLosMotivos() {
        assertEquals("AB123CD no puede salir: Defecto bloqueante: Sin frenos; Seguro: venció el 01/10/2026.",
                VehicleAvailabilityService.blockedMessage("AB123CD", List.of(
                        new BlockReasonDto("blocking_defect", "Defecto bloqueante: Sin frenos"),
                        new BlockReasonDto("expired_document", "Seguro: venció el 01/10/2026"))));
    }

    @Test
    void alBloquearseGuardaDesdeCuandoConLoMasViejoQueLoBloqueaYAvisa() throws Exception {
        Instant reported = Instant.parse("2026-10-20T15:00:00Z");
        when(defectRepository.findOpenBlockingWithInspection(any())).thenReturn(List.of(blockingDefect(vehicle, "Sin frenos", reported)));
        when(documentRepository.findExpiredCurrent(any(), eq(TODAY)))
                .thenReturn(List.of(document(vehicle, VehicleDocumentType.VTV, LocalDate.of(2026, 10, 12))));

        service.sync(vehicle.getId());

        // El VTV venció el 12/10: no puede salir desde el 13/10 a las 00:00 de Argentina.
        assertEquals(Instant.parse("2026-10-13T03:00:00Z"), vehicle.getUnavailableSince());
        ArgumentCaptor<VehicleAvailabilityChangedEvent> event = ArgumentCaptor.forClass(VehicleAvailabilityChangedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertFalse(event.getValue().available());
        assertEquals(2, event.getValue().reasons().size());
        assertEquals(Instant.parse("2026-10-13T03:00:00Z"), event.getValue().unavailableSince());
        verify(vehicleRepository).save(vehicle);
    }

    @Test
    void alLiberarseLimpiaLaMarcaYAvisaConLaOtQueLoLibero() {
        Instant since = Instant.parse("2026-10-20T15:00:00Z");
        vehicle.setUnavailableSince(since);
        UUID workOrderId = UUID.randomUUID();

        service.sync(vehicle, workOrderId);

        assertNull(vehicle.getUnavailableSince());
        ArgumentCaptor<VehicleAvailabilityChangedEvent> event = ArgumentCaptor.forClass(VehicleAvailabilityChangedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertTrue(event.getValue().available());
        assertEquals(since, event.getValue().unavailableSince());
        assertEquals(workOrderId, event.getValue().workOrderId());
    }

    @Test
    void sinCambiosNoPublicaNadaYUnVehiculoDadoDeBajaNoSeRevisa() {
        service.sync(vehicle, null);
        vehicle.setUnavailableSince(NOW);
        when(defectRepository.findOpenBlockingWithInspection(any())).thenReturn(List.of(blockingDefect(vehicle, "Sin frenos", NOW)));
        service.sync(vehicle, null);
        vehicle.setActive(false);
        vehicle.setUnavailableSince(null);
        service.sync(vehicle, null);

        verify(eventPublisher, never()).publishEvent(any());
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void unBloqueoDeRecienNoTieneFechaFuturaYSinVehiculoNoHaceNada() {
        when(documentRepository.findExpiredCurrent(any(), eq(TODAY))).thenReturn(List.of());
        when(defectRepository.findOpenBlockingWithInspection(any())).thenReturn(List.of(blockingDefect(vehicle, "Sin frenos", null)));

        service.sync(vehicle, null);

        assertEquals(NOW, vehicle.getUnavailableSince());
        service.sync(UUID.randomUUID());
    }
}
