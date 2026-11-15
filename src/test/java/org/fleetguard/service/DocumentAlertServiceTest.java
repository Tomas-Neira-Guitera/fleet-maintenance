package org.fleetguard.service;

import org.fleetguard.entity.DocumentAlertLog;
import org.fleetguard.entity.DocumentAlertStage;
import org.fleetguard.entity.NotificationChannel;
import org.fleetguard.entity.NotificationType;
import org.fleetguard.entity.Role;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.VehicleDocument;
import org.fleetguard.entity.VehicleDocumentType;
import org.fleetguard.repository.DocumentAlertLogRepository;
import org.fleetguard.repository.VehicleDocumentRepository;
import org.fleetguard.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.fleetguard.service.VehicleAvailabilityServiceTest.setId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CAM-127: un aviso al entrar en la anticipación, otro el día que vence y otro al vencer, una sola vez cada
 * uno. Sin depender del reloj real: "hoy" es el 29/10.
 */
class DocumentAlertServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 29);

    private final VehicleDocumentRepository documentRepository = mock(VehicleDocumentRepository.class);
    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final DocumentAlertLogRepository logRepository = mock(DocumentAlertLogRepository.class);
    private final NotificationSettingsService settings = mock(NotificationSettingsService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final VehicleAvailabilityService availabilityService = mock(VehicleAvailabilityService.class);
    private final DocumentAlertService service = new DocumentAlertService(documentRepository, vehicleRepository, logRepository,
            settings, notificationService, new NotificationDispatcher(mock(PlatformTransactionManager.class)),
            availabilityService, Clock.fixed(NOW, ZoneOffset.UTC));

    private Vehicle vehicle;

    private VehicleDocument document(VehicleDocumentType type, LocalDate expiresAt) throws Exception {
        VehicleDocument d = new VehicleDocument(vehicle.getId(), type, null, null, null, expiresAt, null, NOW);
        setId(d, UUID.randomUUID());
        return d;
    }

    private void settings(boolean dueSoonEnabled, boolean expiredEnabled, Integer leadDays) {
        when(settings.effective(NotificationType.DOCUMENT_DUE_SOON)).thenReturn(new EffectiveNotificationSetting(
                NotificationType.DOCUMENT_DUE_SOON, dueSoonEnabled, Set.of(NotificationChannel.IN_APP), Set.of(Role.ADMIN),
                Set.of(), leadDays, null, false));
        when(settings.effective(NotificationType.DOCUMENT_EXPIRED)).thenReturn(new EffectiveNotificationSetting(
                NotificationType.DOCUMENT_EXPIRED, expiredEnabled, Set.of(NotificationChannel.IN_APP), Set.of(Role.ADMIN),
                Set.of(), null, null, false));
    }

    @BeforeEach
    void setUp() throws Exception {
        vehicle = new Vehicle("AB123CD", "Iveco", "Daily");
        setId(vehicle, UUID.randomUUID());
        when(vehicleRepository.findAllById(any())).thenReturn(List.of(vehicle));
        when(availabilityService.today()).thenReturn(TODAY);
        settings(true, true, 15);
    }

    @Test
    void cargarUnaVtvQueVenceEn10DiasAvisaAlAdmin() throws Exception {
        VehicleDocument vtv = document(VehicleDocumentType.VTV, TODAY.plusDays(10));
        when(documentRepository.findCurrentWithExpiry()).thenReturn(List.of(vtv));

        assertEquals(1, service.checkVehicle(vehicle.getId()));

        verify(notificationService).notifyConfiguredRecipients(NotificationType.DOCUMENT_DUE_SOON, "Documento por vencer",
                "VTV / RTO de AB123CD: vence el 08/11/2026 (faltan 10 días).", "/vehiculos/" + vehicle.getId() + "/documentacion");
        ArgumentCaptor<DocumentAlertLog> log = ArgumentCaptor.forClass(DocumentAlertLog.class);
        verify(logRepository).saveAndFlush(log.capture());
        assertEquals(DocumentAlertStage.DUE_SOON, log.getValue().getStage());
        assertEquals(vtv.getId(), log.getValue().getDocumentId());
    }

    @Test
    void elDiaQueVenceYAlVencerSaleCadaUnoConSuTipo() throws Exception {
        VehicleDocument today = document(VehicleDocumentType.SEGURO, TODAY);
        VehicleDocument expired = document(VehicleDocumentType.VTV, TODAY.minusDays(1));
        when(documentRepository.findCurrentWithExpiry()).thenReturn(List.of(today, expired));
        when(vehicleRepository.findByActive(true)).thenReturn(List.of(vehicle));

        assertEquals(2, service.checkAll());

        verify(notificationService).notifyConfiguredRecipients(eq(NotificationType.DOCUMENT_DUE_SOON), eq("Documento por vencer"),
                eq("Seguro de AB123CD: vence hoy (29/10/2026). Desde mañana el vehículo queda no disponible si no se carga la renovación."),
                anyString());
        verify(notificationService).notifyConfiguredRecipients(eq(NotificationType.DOCUMENT_EXPIRED), eq("Documento vencido"),
                anyString(), anyString());
        // Los documentos vencen solos con el paso del día: la corrida revisa la disponibilidad de la flota.
        verify(availabilityService).sync(vehicle.getId());
    }

    @Test
    void unAvisoYaMandadoNoSeRepiteYFueraDeLaAnticipacionNoAvisa() throws Exception {
        VehicleDocument sent = document(VehicleDocumentType.VTV, TODAY.plusDays(5));
        VehicleDocument far = document(VehicleDocumentType.SEGURO, TODAY.plusDays(40));
        VehicleDocument noExpiry = document(VehicleDocumentType.TITULO, null);
        when(documentRepository.findCurrentWithExpiry()).thenReturn(List.of(sent, far, noExpiry));
        when(logRepository.existsByDocumentIdAndStageAndExpiresAt(sent.getId(), DocumentAlertStage.DUE_SOON, TODAY.plusDays(5)))
                .thenReturn(true);

        assertEquals(0, service.checkVehicle(vehicle.getId()));
        verify(notificationService, never()).notifyConfiguredRecipients(any(), any(), any(), any());
    }

    @Test
    void conElAvisoApagadoOElVehiculoDadoDeBajaNoAvisa() throws Exception {
        settings(false, false, null);
        when(documentRepository.findCurrentWithExpiry()).thenReturn(List.of(document(VehicleDocumentType.VTV, TODAY)));
        assertEquals(0, service.checkVehicle(vehicle.getId()));

        settings(true, true, null);
        vehicle.setActive(false);
        assertEquals(0, service.checkVehicle(vehicle.getId()));
        verify(notificationService, never()).notifyConfiguredRecipients(any(), any(), any(), any());
    }

    @Test
    void sinAnticipacionGuardadaUsaLosQuinceDiasPorDefectoYSoloRevisaEseVehiculo() throws Exception {
        settings(true, true, null);
        Vehicle other = new Vehicle("ZZ999ZZ", "Ford", "Cargo");
        setId(other, UUID.randomUUID());
        VehicleDocument otherDoc = new VehicleDocument(other.getId(), VehicleDocumentType.VTV, null, null, null,
                TODAY.plusDays(3), null, NOW);
        setId(otherDoc, UUID.randomUUID());
        when(documentRepository.findCurrentWithExpiry()).thenReturn(List.of(document(VehicleDocumentType.VTV, TODAY.plusDays(15)), otherDoc));

        assertEquals(1, service.checkVehicle(vehicle.getId()));
        assertEquals(0, service.checkVehicle(UUID.randomUUID()));
    }
}
