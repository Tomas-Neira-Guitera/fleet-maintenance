package org.fleetguard.service;

import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.entity.VehicleDocument;
import org.fleetguard.entity.VehicleDocumentType;
import org.fleetguard.event.VehicleDocumentChangedEvent;
import org.fleetguard.repository.VehicleDocumentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

/**
 * Guarda documentos y propaga el cambio (CAM-127): fechas de los planes vinculados, disponibilidad del
 * vehículo y avisos. Lo usan la carga de documentos y el registro de un mantenimiento hecho, sin depender
 * uno del otro.
 */
@Component
public class VehicleDocumentWriter {

    static final int TEXT_MAX_LENGTH = 60;

    /** Datos ya validados de un documento. */
    public record Fields(String number, LocalDate issuedAt, LocalDate expiresAt, String fileUrl) {
    }

    private final VehicleDocumentRepository documentRepository;
    private final LinkedDocumentDueDates linkedDueDates;
    private final VehicleAvailabilityService availabilityService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public VehicleDocumentWriter(VehicleDocumentRepository documentRepository, LinkedDocumentDueDates linkedDueDates,
                                 VehicleAvailabilityService availabilityService, ApplicationEventPublisher eventPublisher,
                                 Clock clock) {
        this.documentRepository = documentRepository;
        this.linkedDueDates = linkedDueDates;
        this.availabilityService = availabilityService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /** Valida los campos; los errores van a {@code details} con el prefijo (por ejemplo "document."). */
    static Fields parse(String prefix, String number, String issuedAt, String expiresAt, String fileUrl,
                        boolean expiresRequired, List<FieldValidationErrorDetail> details) {
        String cleanNumber = blankToNull(number);
        if (cleanNumber != null && cleanNumber.length() > TEXT_MAX_LENGTH) {
            details.add(new FieldValidationErrorDetail(prefix + "number", "Hasta " + TEXT_MAX_LENGTH + " caracteres"));
        }
        LocalDate issued = parseDate(prefix + "issuedAt", issuedAt, details);
        LocalDate expires = parseDate(prefix + "expiresAt", expiresAt, details);
        if (expiresRequired && blankToNull(expiresAt) == null) {
            details.add(new FieldValidationErrorDetail(prefix + "expiresAt", "Obligatorio"));
        }
        if (issued != null && expires != null && expires.isBefore(issued)) {
            details.add(new FieldValidationErrorDetail(prefix + "expiresAt", "No puede ser anterior a la emisión"));
        }
        return new Fields(cleanNumber, issued, expires, blankToNull(fileUrl));
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static LocalDate parseDate(String field, String value, List<FieldValidationErrorDetail> details) {
        if (blankToNull(value) == null) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            details.add(new FieldValidationErrorDetail(field, "Fecha inválida, formato esperado YYYY-MM-DD"));
            return null;
        }
    }

    /**
     * Guarda un documento nuevo. Si hay uno vigente del mismo tipo (salvo "otro"), queda como su renovación
     * y el anterior pasa al historial.
     */
    public VehicleDocument renew(UUID vehicleId, VehicleDocumentType type, String name, Fields fields) {
        VehicleDocument previous = type == VehicleDocumentType.OTRO ? null
                : documentRepository.findFirstByVehicleIdAndTypeAndActiveTrueAndReplacedByIdIsNull(vehicleId, type).orElse(null);
        return save(new VehicleDocument(vehicleId, type, name, fields.number(), fields.issuedAt(), fields.expiresAt(),
                fields.fileUrl(), clock.instant()), previous);
    }

    /** Renueva ese documento puntual (también uno de tipo "otro"). */
    public VehicleDocument renewOf(VehicleDocument previous, Fields fields) {
        return save(new VehicleDocument(previous.getVehicleId(), previous.getType(), previous.getName(), fields.number(),
                fields.issuedAt(), fields.expiresAt(), fields.fileUrl(), clock.instant()), previous);
    }

    /** Guarda {@code document}; si reemplaza a {@code previous}, el anterior queda en el historial. */
    public VehicleDocument save(VehicleDocument document, VehicleDocument previous) {
        VehicleDocument saved = documentRepository.saveAndFlush(document);
        if (previous != null) {
            previous.replaceWith(saved.getId());
            documentRepository.saveAndFlush(previous);
        }
        afterChange(saved.getVehicleId(), saved.getType());
        return saved;
    }

    /** Después de cualquier cambio de un documento: planes vinculados, disponibilidad y avisos. */
    public void afterChange(UUID vehicleId, VehicleDocumentType type) {
        documentRepository.flush();
        linkedDueDates.syncVehicle(vehicleId, type);
        availabilityService.sync(vehicleId);
        eventPublisher.publishEvent(new VehicleDocumentChangedEvent(vehicleId));
    }
}
