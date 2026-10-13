package org.fleetguard.service;

import org.fleetguard.auth.AuthenticatedUser;
import org.fleetguard.entity.CheckOutcome;
import org.fleetguard.entity.Inspection;
import org.fleetguard.entity.InspectionAnswer;
import org.fleetguard.entity.InspectionType;
import org.fleetguard.mapper.InspectionMapper;
import org.fleetguard.repository.DefectRepository;
import org.fleetguard.repository.InspectionRepository;
import org.fleetguard.entity.Defect;
import org.fleetguard.entity.DefectSeverity;
import org.fleetguard.exception.InspectionValidationException;
import org.fleetguard.dto.ValidationErrorDetail;
import org.fleetguard.exception.VehicleNotFoundException;
import org.fleetguard.exception.VehicleStateConflictException;
import org.fleetguard.dto.ChecklistAnswerDto;
import org.fleetguard.dto.InspectionResultDto;
import org.fleetguard.dto.InspectionSubmissionDto;
import org.fleetguard.entity.Trip;
import org.fleetguard.mapper.TripMapper;
import org.fleetguard.repository.TripRepository;
import org.fleetguard.entity.TripStatus;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.checklist.ChecklistItemDef;
import org.fleetguard.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Caso de uso central de CAM-11: POST /api/inspections/{vehicleId}. Ver CAM-11-dvir-contract.md secciones 4 y 5. */
@Service
public class InspectionService {

    private final VehicleRepository vehicleRepository;
    private final TripRepository tripRepository;
    private final InspectionRepository inspectionRepository;
    private final DefectRepository defectRepository;
    private final InspectionValidator validator;
    private final InspectionMapper inspectionMapper;
    private final TripMapper tripMapper;
    private final VehicleChecklistService checklistService;
    private final OdometerReadings odometerReadings;

    public InspectionService(VehicleRepository vehicleRepository, TripRepository tripRepository,
                              InspectionRepository inspectionRepository, DefectRepository defectRepository,
                              InspectionMapper inspectionMapper, TripMapper tripMapper,
                              VehicleChecklistService checklistService, OdometerReadings odometerReadings) {
        this.vehicleRepository = vehicleRepository;
        this.tripRepository = tripRepository;
        this.inspectionRepository = inspectionRepository;
        this.defectRepository = defectRepository;
        this.validator = new InspectionValidator();
        this.inspectionMapper = inspectionMapper;
        this.tripMapper = tripMapper;
        this.checklistService = checklistService;
        this.odometerReadings = odometerReadings;
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
        Map<String, String> labelById = checklist.stream()
                .collect(Collectors.toMap(ChecklistItemDef::id, ChecklistItemDef::label));

        Instant now = Instant.now();
        Trip trip;
        if (type == InspectionType.PRE_TRIP) {
            trip = new Trip(vehicle, now, driver.id(), driver.username());
            // CAM-86: con un defecto bloqueante el vehículo no sale, así que no queda en viaje.
            // inspections.trip_id es obligatorio: el viaje se abre y se cierra en el mismo instante.
            if (outcome.hasBlockingDefect()) {
                trip.close(now, driver.id(), driver.username());
            }
        } else {
            trip = openTrip.get();
            trip.close(now, driver.id(), driver.username());
        }
        tripRepository.save(trip);

        Inspection inspection = new Inspection(trip, vehicleId, driver.id().toString(), driver.username(), type, now,
                outcome.odometerKm(), submission.notes(), outcome.hasBlockingDefect());

        for (ChecklistAnswerDto answerDto : outcome.recognizedAnswers()) {
            CheckOutcome checkOutcome = CheckOutcome.fromJson(answerDto.outcome());
            InspectionAnswer answerEntity = new InspectionAnswer(answerDto.itemId(), labelById.get(answerDto.itemId()),
                    checkOutcome, answerDto.numberValue());
            inspection.addAnswer(answerEntity);

            if (checkOutcome == CheckOutcome.DEFECT && answerDto.defect() != null) {
                DefectSeverity severity = DefectSeverity.fromJson(answerDto.defect().severity());
                String defectDetails = answerDto.defect().details();
                Defect defect = new Defect(severity, answerDto.defect().description(),
                        defectDetails == null || defectDetails.isBlank() ? null : defectDetails.trim(),
                        answerDto.defect().photoUrl(), now);
                answerEntity.attachDefect(defect);
            }
        }

        inspectionRepository.save(inspection);

        // CAM-90: el km que cargó el chofer avanza el del vehículo (parte entera; la inspección guarda
        // el valor tal cual). Si no pasa la regla de OdometerReadings, la inspección vale igual.
        if (outcome.odometerKm() != null) {
            odometerReadings.apply(vehicle, (long) Math.floor(outcome.odometerKm()));
        }

        return new InspectionResultDto(inspectionMapper.toDto(inspection), tripMapper.toDto(trip));
    }

    /** CAM-89: el servidor es el que impide abrir un viaje con un vehículo que no puede salir. */
    private void rejectIfCannotLeave(Vehicle vehicle, boolean hasOpenTrip) {
        if (!vehicle.isActive()) {
            throw new VehicleStateConflictException("VEHICLE_INACTIVE", "El vehículo está dado de baja.");
        }
        if (hasOpenTrip) {
            throw new VehicleStateConflictException("VEHICLE_ON_TRIP", "El vehículo ya tiene un pre-trip abierto.");
        }
        if (defectRepository.existsOpenBlockingByVehicleId(vehicle.getId())) {
            throw new VehicleStateConflictException("VEHICLE_BLOCKED",
                    "El vehículo tiene un defecto bloqueante sin resolver y no puede salir.");
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
