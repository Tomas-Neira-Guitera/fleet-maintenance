package org.fleetguard.service;

import org.fleetguard.auth.Driver;
import org.fleetguard.entity.CheckOutcome;
import org.fleetguard.entity.Inspection;
import org.fleetguard.entity.InspectionAnswer;
import org.fleetguard.entity.InspectionType;
import org.fleetguard.mapper.InspectionMapper;
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
    private final InspectionValidator validator;
    private final InspectionMapper inspectionMapper;
    private final TripMapper tripMapper;
    private final VehicleChecklistService checklistService;

    public InspectionService(VehicleRepository vehicleRepository, TripRepository tripRepository,
                              InspectionRepository inspectionRepository, InspectionMapper inspectionMapper,
                              TripMapper tripMapper, VehicleChecklistService checklistService) {
        this.vehicleRepository = vehicleRepository;
        this.tripRepository = tripRepository;
        this.inspectionRepository = inspectionRepository;
        this.validator = new InspectionValidator();
        this.inspectionMapper = inspectionMapper;
        this.tripMapper = tripMapper;
        this.checklistService = checklistService;
    }

    @Transactional
    public InspectionResultDto submit(String vehicleIdRaw, InspectionSubmissionDto submission, Driver driver) {
        UUID vehicleId = parseVehicleId(vehicleIdRaw);
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new VehicleNotFoundException(vehicleIdRaw));

        InspectionType type = InspectionType.fromJson(submission.type());
        if (type == null) {
            throw new InspectionValidationException("El campo type debe ser 'pre-trip' o 'post-trip'.",
                    List.of(new ValidationErrorDetail("type", "Valor inválido.")));
        }

        Optional<Trip> openTrip = tripRepository.findFirstByVehicle_IdAndStatus(vehicleId, TripStatus.OPEN);

        if (type == InspectionType.PRE_TRIP && openTrip.isPresent()) {
            throw new VehicleStateConflictException("VEHICLE_ON_TRIP", "El vehículo ya tiene un pre-trip abierto.");
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
            trip = new Trip(vehicle, now);
            tripRepository.save(trip);
        } else {
            trip = openTrip.get();
            trip.close(now);
            tripRepository.save(trip);
        }

        Inspection inspection = new Inspection(trip, vehicleId, driver.id(), driver.name(), type, now,
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

        return new InspectionResultDto(inspectionMapper.toDto(inspection), tripMapper.toDto(trip));
    }

    private UUID parseVehicleId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new VehicleNotFoundException(raw);
        }
    }
}
