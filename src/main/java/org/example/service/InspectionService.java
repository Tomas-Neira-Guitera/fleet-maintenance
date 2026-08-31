package org.example.service;

import org.example.auth.Driver;
import org.example.entity.CheckOutcome;
import org.example.entity.Inspection;
import org.example.entity.InspectionAnswer;
import org.example.entity.InspectionType;
import org.example.mapper.InspectionMapper;
import org.example.repository.InspectionRepository;
import org.example.entity.Defect;
import org.example.entity.DefectSeverity;
import org.example.exception.InspectionValidationException;
import org.example.dto.ValidationErrorDetail;
import org.example.exception.VehicleNotFoundException;
import org.example.exception.VehicleStateConflictException;
import org.example.dto.ChecklistAnswerDto;
import org.example.dto.InspectionResultDto;
import org.example.dto.InspectionSubmissionDto;
import org.example.entity.Trip;
import org.example.mapper.TripMapper;
import org.example.repository.TripRepository;
import org.example.entity.TripStatus;
import org.example.entity.Vehicle;
import org.example.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Caso de uso central de CAM-11: POST /api/inspections/{vehicleId}. Ver CAM-11-dvir-contract.md secciones 4 y 5. */
@Service
public class InspectionService {

    private final VehicleRepository vehicleRepository;
    private final TripRepository tripRepository;
    private final InspectionRepository inspectionRepository;
    private final InspectionValidator validator;
    private final InspectionMapper inspectionMapper;
    private final TripMapper tripMapper;

    public InspectionService(VehicleRepository vehicleRepository, TripRepository tripRepository,
                              InspectionRepository inspectionRepository, InspectionMapper inspectionMapper,
                              TripMapper tripMapper) {
        this.vehicleRepository = vehicleRepository;
        this.tripRepository = tripRepository;
        this.inspectionRepository = inspectionRepository;
        this.validator = new InspectionValidator();
        this.inspectionMapper = inspectionMapper;
        this.tripMapper = tripMapper;
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
        InspectionValidator.ValidationOutcome outcome = validator.validate(type, answers);

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
            InspectionAnswer answerEntity = new InspectionAnswer(answerDto.itemId(), checkOutcome, answerDto.numberValue());
            inspection.addAnswer(answerEntity);

            if (checkOutcome == CheckOutcome.DEFECT && answerDto.defect() != null) {
                DefectSeverity severity = DefectSeverity.fromJson(answerDto.defect().severity());
                Defect defect = new Defect(severity, answerDto.defect().description(), answerDto.defect().photoUrl(), now);
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
