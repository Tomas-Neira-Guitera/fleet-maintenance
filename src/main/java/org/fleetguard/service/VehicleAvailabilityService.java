package org.fleetguard.service;

import org.fleetguard.dto.BlockReasonDto;
import org.fleetguard.entity.Defect;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.VehicleDocument;
import org.fleetguard.event.VehicleAvailabilityChangedEvent;
import org.fleetguard.repository.DefectRepository;
import org.fleetguard.repository.VehicleDocumentRepository;
import org.fleetguard.repository.VehicleRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Único lugar que decide si un vehículo puede salir (CAM-89, CAM-127): un defecto bloqueante abierto o
 * un documento vigente vencido lo dejan "No disponible". sync detecta el cambio y lo publica (CAM-102/103).
 */
@Service
public class VehicleAvailabilityService {

    static final ZoneId FLEET_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final DefectRepository defectRepository;
    private final VehicleDocumentRepository documentRepository;
    private final VehicleRepository vehicleRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public VehicleAvailabilityService(DefectRepository defectRepository, VehicleDocumentRepository documentRepository,
                                      VehicleRepository vehicleRepository, ApplicationEventPublisher eventPublisher,
                                      Clock clock) {
        this.defectRepository = defectRepository;
        this.documentRepository = documentRepository;
        this.vehicleRepository = vehicleRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(FLEET_ZONE));
    }

    /** Vencido = el día siguiente al vencimiento: el día que vence todavía se puede circular. */
    public static boolean isExpired(LocalDate expiresAt, LocalDate today) {
        return expiresAt != null && today.isAfter(expiresAt);
    }

    public List<BlockReasonDto> reasons(UUID vehicleId) {
        return reasonsFor(List.of(vehicleId)).getOrDefault(vehicleId, List.of());
    }

    /** Motivos por vehículo, en dos consultas para todo el listado. Los que pueden salir no aparecen. */
    public Map<UUID, List<BlockReasonDto>> reasonsFor(Collection<UUID> vehicleIds) {
        Map<UUID, List<BlockReasonDto>> result = new HashMap<>();
        if (vehicleIds.isEmpty()) {
            return result;
        }
        for (Defect defect : defectRepository.findOpenBlockingWithInspection(vehicleIds)) {
            UUID vehicleId = defect.getInspectionAnswer().getInspection().getVehicleId();
            result.computeIfAbsent(vehicleId, id -> new ArrayList<>()).add(new BlockReasonDto(
                    BlockReasonDto.BLOCKING_DEFECT, "Defecto bloqueante: " + defect.getDescription()));
        }
        List<VehicleDocument> expired = documentRepository.findExpiredCurrent(vehicleIds, today()).stream()
                .sorted(Comparator.comparing(VehicleDocument::getExpiresAt)).toList();
        for (VehicleDocument document : expired) {
            result.computeIfAbsent(document.getVehicleId(), id -> new ArrayList<>()).add(new BlockReasonDto(
                    BlockReasonDto.EXPIRED_DOCUMENT,
                    document.getDisplayName() + ": venció el " + DATE.format(document.getExpiresAt())));
        }
        return result;
    }

    /** Texto para el 409 VEHICLE_BLOCKED: "AB123CD no puede salir: VTV / RTO: venció el 12/10/2026." */
    public static String blockedMessage(String plate, List<BlockReasonDto> reasons) {
        return plate + " no puede salir: " + String.join("; ", reasons.stream().map(BlockReasonDto::message).toList()) + ".";
    }

    @Transactional
    public void sync(UUID vehicleId) {
        vehicleRepository.findById(vehicleId).ifPresent(vehicle -> sync(vehicle, null));
    }

    /**
     * Recalcula la disponibilidad y, si cambió desde la última vez, la guarda y publica el evento (sale
     * después del commit). workOrderId: la OT que se acaba de finalizar, si es lo que la originó.
     */
    @Transactional
    public void sync(Vehicle vehicle, UUID workOrderId) {
        if (!vehicle.isActive()) {
            return;
        }
        List<BlockReasonDto> reasons = reasons(vehicle.getId());
        Instant since = vehicle.getUnavailableSince();
        if (!reasons.isEmpty() && since == null) {
            Instant blockedSince = blockedSince(vehicle.getId());
            vehicle.setUnavailableSince(blockedSince);
            vehicleRepository.save(vehicle);
            eventPublisher.publishEvent(new VehicleAvailabilityChangedEvent(vehicle.getId(), vehicle.getPlate(), false,
                    reasons, blockedSince, null));
        } else if (reasons.isEmpty() && since != null) {
            vehicle.setUnavailableSince(null);
            vehicleRepository.save(vehicle);
            eventPublisher.publishEvent(new VehicleAvailabilityChangedEvent(vehicle.getId(), vehicle.getPlate(), true,
                    List.of(), since, workOrderId));
        }
    }

    /** Lo más viejo que lo bloquea hoy: el primer bloqueante reportado o el día siguiente al vencimiento. */
    private Instant blockedSince(UUID vehicleId) {
        Instant now = clock.instant();
        Stream<Instant> defects = defectRepository.findOpenBlockingWithInspection(List.of(vehicleId)).stream()
                .map(Defect::getCreatedAt);
        Stream<Instant> documents = documentRepository.findExpiredCurrent(List.of(vehicleId), today()).stream()
                .map(d -> d.getExpiresAt().plusDays(1).atStartOfDay(FLEET_ZONE).toInstant());
        return Stream.concat(defects, documents).filter(i -> i != null && i.isBefore(now))
                .min(Comparator.naturalOrder()).orElse(now);
    }
}
