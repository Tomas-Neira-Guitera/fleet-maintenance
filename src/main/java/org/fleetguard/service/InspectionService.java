package org.fleetguard.service;

import org.fleetguard.auth.AuthenticatedUser;
import org.fleetguard.entity.CheckOutcome;
import org.fleetguard.entity.Inspection;
import org.fleetguard.entity.InspectionAnswer;
import org.fleetguard.entity.InspectionType;
import org.fleetguard.mapper.InspectionMapper;
import org.fleetguard.repository.InspectionRepository;
import org.fleetguard.entity.Defect;
import org.fleetguard.event.BlockingDefectReportedEvent;
import org.fleetguard.event.DefectsReportedEvent;
import org.fleetguard.event.TripStartedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.fleetguard.entity.DefectSeverity;
import org.fleetguard.exception.InspectionValidationException;
import org.fleetguard.dto.ValidationErrorDetail;
import org.fleetguard.exception.VehicleNotFoundException;
import org.fleetguard.exception.VehicleStateConflictException;
import org.fleetguard.dto.BlockReasonDto;
import org.fleetguard.dto.ChecklistAnswerDto;
import org.fleetguard.dto.InspectionDto;
import org.fleetguard.dto.InspectionResultDto;
import org.fleetguard.dto.InspectionSubmissionDto;
import org.fleetguard.entity.Trip;
import org.fleetguard.mapper.TripMapper;
import org.fleetguard.repository.TripRepository;
import org.fleetguard.entity.TripStatus;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.checklist.ChecklistCatalog;
import org.fleetguard.entity.checklist.ChecklistItemDef;
import org.fleetguard.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Caso de uso central de CAM-11: POST /api/inspections/{vehicleId}. Ver CAM-11-dvir-contract.md secciones 4 y 5. */
@Service
public class InspectionService {

    // CAM-157: el control del checklist base al que van los neumáticos con problemas.
    static final String TIRES_ITEM_ID = "ext-neumaticos";

    // CAM-153: margen para un reloj del celular apenas adelantado; más que esto es un error.
    static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);

    private final VehicleRepository vehicleRepository;
    private final TripRepository tripRepository;
    private final InspectionRepository inspectionRepository;
    private final InspectionValidator validator;
    private final InspectionMapper inspectionMapper;
    private final TripMapper tripMapper;
    private final VehicleChecklistService checklistService;
    private final OdometerReadings odometerReadings;
    private final VehicleAvailabilityService availabilityService;
    private final TripKmAlertService tripKmAlertService;
    private final TireService tireService;
    private final CouplingService couplingService;

    private final ApplicationEventPublisher eventPublisher;

    public InspectionService(VehicleRepository vehicleRepository, TripRepository tripRepository,
                              InspectionRepository inspectionRepository,
                              InspectionMapper inspectionMapper, TripMapper tripMapper,
                              VehicleChecklistService checklistService, OdometerReadings odometerReadings,
                              VehicleAvailabilityService availabilityService, TripKmAlertService tripKmAlertService,
                              ApplicationEventPublisher eventPublisher, TireService tireService,
                              CouplingService couplingService) {
        this.tripKmAlertService = tripKmAlertService;
        this.tireService = tireService;
        this.couplingService = couplingService;
        this.vehicleRepository = vehicleRepository;
        this.tripRepository = tripRepository;
        this.inspectionRepository = inspectionRepository;
        this.validator = new InspectionValidator();
        this.inspectionMapper = inspectionMapper;
        this.tripMapper = tripMapper;
        this.checklistService = checklistService;
        this.odometerReadings = odometerReadings;
        this.availabilityService = availabilityService;
        this.eventPublisher = eventPublisher;
    }

    /** driver: el chofer del JWT (CAM-85); queda en la inspección y en el viaje que abre o cierra (CAM-87). */
    @Transactional
    public InspectionResultDto submit(String vehicleIdRaw, InspectionSubmissionDto submission, AuthenticatedUser driver) {
        UUID vehicleId = parseVehicleId(vehicleIdRaw);
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new VehicleNotFoundException(vehicleIdRaw));

        InspectionType type = InspectionType.fromJson(submission.type());
        if (type == null) {
            throw new InspectionValidationException("El campo type debe ser 'pre-trip' o 'post-trip'.",
                    List.of(new ValidationErrorDetail("type", "Valor inválido.")));
        }
        // CAM-157: un remolque no tiene chofer ni viajes propios; se revisa desde el admin.
        if (vehicle.getVehicleType() != null && vehicle.getVehicleType().isTrailer()) {
            throw new VehicleStateConflictException("VEHICLE_IS_TRAILER",
                    vehicle.getPlate() + " es un remolque: se revisa en el pre-viaje del vehículo que lo arrastra.");
        }

        // CAM-153: un reintento de una inspección que ya llegó devuelve la guardada, sin volver a validar el
        // estado del vehículo (que ya cambió por esa misma inspección).
        String clientSubmissionId = parseClientSubmissionId(submission.clientSubmissionId());
        if (clientSubmissionId != null) {
            Optional<Inspection> existing = inspectionRepository.findByClientSubmissionId(clientSubmissionId);
            if (existing.isPresent()) {
                return replay(existing.get(), vehicleId, driver);
            }
        }

        Optional<Trip> openTrip = tripRepository.findFirstByVehicle_IdAndStatus(vehicleId, TripStatus.OPEN);

        if (type == InspectionType.PRE_TRIP) {
            rejectIfCannotLeave(vehicle, openTrip.isPresent());
        }
        if (type == InspectionType.POST_TRIP && openTrip.isEmpty()) {
            throw new VehicleStateConflictException("NO_OPEN_TRIP", "El vehículo no tiene un viaje abierto para cerrar.");
        }

        List<ChecklistAnswerDto> answers = submission.answers() == null ? List.of() : submission.answers();
        List<ChecklistItemDef> checklist = checklistService.resolve(vehicleId, type);
        InspectionValidator.ValidationOutcome outcome = validator.validate(type, checklist, answers);
        // CAM-157: qué neumáticos tienen problemas, solo si "Neumáticos" va en defecto.
        boolean tiresInDefect = outcome.recognizedAnswers().stream().anyMatch(a -> TIRES_ITEM_ID.equals(a.itemId())
                && CheckOutcome.fromJson(a.outcome()) == CheckOutcome.DEFECT && a.defect() != null);
        List<TireService.ConditionReport> tireReports =
                tireService.validateInspectionConditions(vehicle, submission.tireConditions(), tiresInDefect);
        String trailerIdRaw = type == InspectionType.PRE_TRIP ? submission.trailerId() : null;
        Optional<Vehicle> trailer = couplingService.resolveInspectionTrailer(vehicle, trailerIdRaw);
        // CAM-159: el remolque se revisa en el pre-viaje (el elegido o, sin elegir, el que ya llevaba) y en el
        // post-viaje (el que lleva). En el post-viaje es opcional: el viaje se tiene que poder cerrar siempre.
        Optional<Vehicle> towedTrailer = trailerIdRaw == null || trailerIdRaw.isBlank()
                ? couplingService.currentTrailer(vehicle) : trailer;
        TrailerCheck trailerCheck = towedTrailer
                .filter(t -> type == InspectionType.PRE_TRIP || submission.trailerAnswers() != null)
                .map(t -> checkTrailer(t, submission, type)).orElse(null);
        boolean blocking = outcome.hasBlockingDefect() || (trailerCheck != null && trailerCheck.outcome().hasBlockingDefect());

        // CAM-153: el viaje, la inspección y los defectos quedan con la hora en que el chofer la hizo.
        Instant now = performedAt(submission.performedAt(), openTrip.filter(t -> type == InspectionType.POST_TRIP));
        Trip trip;
        if (type == InspectionType.PRE_TRIP) {
            trip = new Trip(vehicle, now, driver.id(), driver.username());
            // CAM-86: con un defecto bloqueante el vehículo no sale, así que no queda en viaje. Tampoco si lo tiene
            // su remolque (CAM-159). inspections.trip_id es obligatorio: el viaje se abre y se cierra en el mismo instante.
            if (blocking) {
                trip.close(now, driver.id(), driver.username());
            }
        } else {
            trip = openTrip.get();
            trip.close(now, driver.id(), driver.username());
        }
        tripRepository.save(trip);

        Inspection inspection = new Inspection(trip, vehicleId, driver.id().toString(), driver.username(), type, now,
                outcome.odometerKm(), submission.notes(), outcome.hasBlockingDefect());
        inspection.setClientSubmissionId(clientSubmissionId);
        addAnswers(inspection, vehicle, checklist, outcome, tireReports, TIRES_ITEM_ID, now);
        inspectionRepository.save(inspection);
        tireService.recordInspectionConditions(vehicle, tireReports, now, driver, inspection.getId(),
                defectOf(inspection, TIRES_ITEM_ID));

        // CAM-159: la del remolque va aparte, a su nombre y en el mismo viaje, ligada a la del vehículo.
        Inspection trailerInspection = null;
        if (trailerCheck != null) {
            Vehicle towed = trailerCheck.trailer();
            trailerInspection = new Inspection(trip, towed.getId(), driver.id().toString(), driver.username(),
                    type, now, null, null, trailerCheck.outcome().hasBlockingDefect());
            trailerInspection.setTowingInspectionId(inspection.getId());
            addAnswers(trailerInspection, towed, trailerCheck.checklist(), trailerCheck.outcome(), trailerCheck.tireReports(),
                    trailerCheck.tiresItemId(), now);
            inspectionRepository.save(trailerInspection);
            tireService.recordInspectionConditions(towed, trailerCheck.tireReports(), now, driver, trailerInspection.getId(),
                    defectOf(trailerInspection, trailerCheck.tiresItemId()));
        }
        couplingService.applyInspectionTrailer(vehicle, trailerIdRaw, trailer, driver.username());

        publishDefectEvents(inspection, vehicle, type, driver);
        if (trailerInspection != null) {
            publishDefectEvents(trailerInspection, trailerCheck.trailer(), type, driver);
        }

        // CAM-143: si salió de viaje con turno de taller ese día, después del commit se avisa al admin.
        if (type == InspectionType.PRE_TRIP && !blocking) {
            eventPublisher.publishEvent(new TripStartedEvent(vehicleId, vehicle.getPlate(), driver.username(), now));
        }

        // CAM-152: antes de que el post-viaje mueva el km del vehículo, se compara con el que tenía y
        // con lo que suele recorrer. Si está fuera de lo normal, marca el viaje y avisa después del commit.
        if (type == InspectionType.POST_TRIP && outcome.odometerKm() != null) {
            tripKmAlertService.check(trip, (long) Math.floor(outcome.odometerKm()), vehicle.getOdometerKm(),
                    driver.username());
        }

        // CAM-90: el km que cargó el chofer avanza el del vehículo (parte entera; la inspección guarda
        // el valor tal cual). Si no pasa la regla de OdometerReadings, la inspección vale igual.
        if (outcome.odometerKm() != null) {
            odometerReadings.apply(vehicle, (long) Math.floor(outcome.odometerKm()));
        }

        return new InspectionResultDto(inspectionMapper.toDto(inspection), tripMapper.toDto(trip),
                trailerInspection == null ? null : inspectionMapper.toDto(trailerInspection));
    }

    /** CAM-159: el remolque ya validado, para guardarlo después de la inspección del vehículo. */
    private record TrailerCheck(Vehicle trailer, List<ChecklistItemDef> checklist, InspectionValidator.ValidationOutcome outcome,
                                String tiresItemId, List<TireService.ConditionReport> tireReports) {
    }

    /**
     * CAM-159: el checklist del remolque, con las mismas reglas que el del vehículo. En el pre-viaje, un remolque que no
     * puede salir (bloqueante abierto o documento vencido, CAM-127) frena el envío entero: hay que elegir otro.
     */
    private TrailerCheck checkTrailer(Vehicle trailer, InspectionSubmissionDto submission, InspectionType type) {
        List<BlockReasonDto> reasons = type == InspectionType.PRE_TRIP ? availabilityService.reasons(trailer.getId()) : List.of();
        if (!reasons.isEmpty()) {
            throw new VehicleStateConflictException("TRAILER_BLOCKED",
                    VehicleAvailabilityService.blockedMessage(trailer.getPlate(), reasons));
        }
        if (submission.trailerAnswers() == null) {
            throw new InspectionValidationException("Falta el checklist del remolque " + trailer.getPlate() + ".",
                    List.of(new ValidationErrorDetail("trailerAnswers", "Obligatorio cuando se lleva un remolque.")));
        }
        List<ChecklistItemDef> checklist = checklistService.resolve(trailer.getId(), type);
        InspectionValidator.ValidationOutcome outcome = validator.validate(type, checklist, submission.trailerAnswers());
        String tiresItemId = type == InspectionType.PRE_TRIP
                ? ChecklistCatalog.TRAILER_TIRES_ITEM_ID : ChecklistCatalog.TRAILER_POST_TRIP_TIRES_ITEM_ID;
        boolean tiresInDefect = outcome.recognizedAnswers().stream()
                .anyMatch(a -> tiresItemId.equals(a.itemId())
                        && CheckOutcome.fromJson(a.outcome()) == CheckOutcome.DEFECT && a.defect() != null);
        List<TireService.ConditionReport> tireReports = tireService.validateInspectionConditions(trailer,
                submission.trailerTireConditions(), tiresInDefect, "trailerTireConditions");
        return new TrailerCheck(trailer, checklist, outcome, tiresItemId, tireReports);
    }

    /** Las respuestas y sus defectos. Los neumáticos con problemas se suman al detalle del defecto de Neumáticos. */
    private void addAnswers(Inspection inspection, Vehicle vehicle, List<ChecklistItemDef> checklist,
                            InspectionValidator.ValidationOutcome outcome, List<TireService.ConditionReport> tireReports,
                            String tiresItemId, Instant now) {
        Map<String, String> labelById = checklist.stream()
                .collect(Collectors.toMap(ChecklistItemDef::id, ChecklistItemDef::label));
        for (ChecklistAnswerDto answerDto : outcome.recognizedAnswers()) {
            CheckOutcome checkOutcome = CheckOutcome.fromJson(answerDto.outcome());
            InspectionAnswer answerEntity = new InspectionAnswer(answerDto.itemId(), labelById.get(answerDto.itemId()),
                    checkOutcome, answerDto.numberValue());
            inspection.addAnswer(answerEntity);

            if (checkOutcome == CheckOutcome.DEFECT && answerDto.defect() != null) {
                DefectSeverity severity = DefectSeverity.fromJson(answerDto.defect().severity());
                String defectDetails = answerDto.defect().details();
                if (tiresItemId.equals(answerDto.itemId()) && !tireReports.isEmpty()) {
                    String summary = tireService.summary(vehicle, tireReports);
                    defectDetails = defectDetails == null || defectDetails.isBlank() ? summary
                            : defectDetails.trim() + "\n\n" + summary;
                }
                Defect defect = new Defect(severity, answerDto.defect().description(),
                        defectDetails == null || defectDetails.isBlank() ? null : defectDetails.trim(),
                        answerDto.defect().photoUrl(), now);
                answerEntity.attachDefect(defect);
            }
        }
    }

    private static Defect defectOf(Inspection inspection, String itemId) {
        return inspection.getAnswers().stream()
                .filter(answer -> itemId.equals(answer.getItemId()))
                .map(InspectionAnswer::getDefect)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
    }

    /** Los avisos de los defectos de una inspección, para el vehículo de esa inspección. Salen después del commit. */
    private void publishDefectEvents(Inspection inspection, Vehicle vehicle, InspectionType type, AuthenticatedUser driver) {
        // CAM-97: el aviso de bloqueante (BlockingDefectNotificationListener).
        List<String> blockingTitles = inspection.getAnswers().stream()
                .map(InspectionAnswer::getDefect)
                .filter(defect -> defect != null && defect.getSeverity() == DefectSeverity.BLOCKING)
                .map(Defect::getDescription)
                .toList();
        if (!blockingTitles.isEmpty()) {
            eventPublisher.publishEvent(new BlockingDefectReportedEvent(vehicle.getId(), vehicle.getPlate(), type,
                    driver.username(), blockingTitles));
            // CAM-102/103: registra desde cuándo no está disponible, para avisar cuando vuelva.
            availabilityService.sync(vehicle, null);
        }

        // CAM-135: con cualquier defecto, se revisa si el control se volvió recurrente.
        List<String> failedItemIds = inspection.getAnswers().stream()
                .filter(answer -> answer.getDefect() != null)
                .map(InspectionAnswer::getItemId)
                .toList();
        if (!failedItemIds.isEmpty()) {
            eventPublisher.publishEvent(new DefectsReportedEvent(vehicle.getId(), vehicle.getPlate(), failedItemIds));
        }
    }

    /**
     * CAM-153: sin performedAt, ahora. Un poco adelantada (reloj del celular) se toma como ahora; más adelantada
     * es un error. Un post-viaje no puede ser anterior al inicio de su viaje: se lleva a ese momento.
     */
    private static Instant performedAt(String raw, Optional<Trip> tripToClose) {
        Instant now = Instant.now();
        if (raw == null || raw.isBlank()) {
            return now;
        }
        Instant at;
        try {
            at = Instant.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new InspectionValidationException("El campo performedAt no es una fecha válida.",
                    List.of(new ValidationErrorDetail("performedAt", "Tiene que ser una fecha ISO-8601 con zona.")));
        }
        if (at.isAfter(now.plus(MAX_CLOCK_SKEW))) {
            throw new InspectionValidationException("La fecha de la inspección es posterior a la actual.",
                    List.of(new ValidationErrorDetail("performedAt", "No puede ser una fecha futura.")));
        }
        if (at.isAfter(now)) {
            at = now;
        }
        Instant tripStart = tripToClose.map(Trip::getStartedAt).orElse(null);
        return tripStart != null && at.isBefore(tripStart) ? tripStart : at;
    }

    private static String parseClientSubmissionId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim()).toString();
        } catch (IllegalArgumentException e) {
            throw new InspectionValidationException("El campo clientSubmissionId no es un UUID.",
                    List.of(new ValidationErrorDetail("clientSubmissionId", "Valor inválido.")));
        }
    }

    /** CAM-153: la misma respuesta que la primera vez. Si el id es de otro chofer u otro vehículo, es un conflicto. */
    private InspectionResultDto replay(Inspection existing, UUID vehicleId, AuthenticatedUser driver) {
        if (!existing.getVehicleId().equals(vehicleId) || !existing.getDriverId().equals(driver.id().toString())) {
            throw new VehicleStateConflictException("DUPLICATE_SUBMISSION",
                    "Ya hay otra inspección guardada con ese clientSubmissionId.");
        }
        InspectionDto trailerInspection = inspectionRepository.findFirstByTowingInspectionId(existing.getId())
                .map(inspectionMapper::toDto).orElse(null);
        return new InspectionResultDto(inspectionMapper.toDto(existing), tripMapper.toDto(existing.getTrip()),
                trailerInspection);
    }

    /** CAM-89: el servidor es el que impide abrir un viaje con un vehículo que no puede salir. */
    private void rejectIfCannotLeave(Vehicle vehicle, boolean hasOpenTrip) {
        if (!vehicle.isActive()) {
            throw new VehicleStateConflictException("VEHICLE_INACTIVE", "El vehículo está dado de baja.");
        }
        if (hasOpenTrip) {
            throw new VehicleStateConflictException("VEHICLE_ON_TRIP", "El vehículo ya tiene un pre-trip abierto.");
        }
        // CAM-127: un defecto bloqueante abierto o un documento vencido.
        List<BlockReasonDto> reasons = availabilityService.reasons(vehicle.getId());
        if (!reasons.isEmpty()) {
            throw new VehicleStateConflictException("VEHICLE_BLOCKED",
                    VehicleAvailabilityService.blockedMessage(vehicle.getPlate(), reasons));
        }
    }

    private UUID parseVehicleId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new VehicleNotFoundException(raw);
        }
    }
}
