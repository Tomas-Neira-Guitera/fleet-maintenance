package org.fleetguard.service;

import org.fleetguard.auth.AuthenticatedUser;
import org.fleetguard.dto.ChecklistAnswerDto;
import org.fleetguard.dto.DefectDto;
import org.fleetguard.dto.RoadDefectRequest;
import org.fleetguard.dto.ValidationErrorDetail;
import org.fleetguard.entity.CheckOutcome;
import org.fleetguard.entity.Defect;
import org.fleetguard.entity.DefectSeverity;
import org.fleetguard.entity.Inspection;
import org.fleetguard.entity.InspectionAnswer;
import org.fleetguard.entity.InspectionType;
import org.fleetguard.entity.Trip;
import org.fleetguard.entity.TripStatus;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.checklist.ChecklistCatalog;
import org.fleetguard.entity.checklist.ChecklistItemDef;
import org.fleetguard.entity.checklist.ChecklistItemType;
import org.fleetguard.event.BlockingDefectReportedEvent;
import org.fleetguard.event.DefectsReportedEvent;
import org.fleetguard.exception.InspectionValidationException;
import org.fleetguard.exception.VehicleNotFoundException;
import org.fleetguard.exception.VehicleStateConflictException;
import org.fleetguard.mapper.DefectMapper;
import org.fleetguard.repository.InspectionRepository;
import org.fleetguard.repository.TripRepository;
import org.fleetguard.repository.VehicleRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CAM-139: POST /api/vehicles/{id}/road-defects. El chofer reporta un defecto durante su viaje, sin
 * cerrarlo. Se guarda como una inspección ON_ROAD de una sola respuesta dentro del viaje abierto, así el
 * defecto cuenta para la disponibilidad, los recurrentes, el tiempo fuera de servicio y las OTs como
 * cualquier otro. Ver CAM-139-road-defect-contract.md.
 */
@Service
public class RoadDefectService {

    private final VehicleRepository vehicleRepository;
    private final TripRepository tripRepository;
    private final InspectionRepository inspectionRepository;
    private final VehicleChecklistService checklistService;
    private final VehicleAvailabilityService availabilityService;
    private final DefectMapper defectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final InspectionValidator validator = new InspectionValidator();

    public RoadDefectService(VehicleRepository vehicleRepository, TripRepository tripRepository,
                             InspectionRepository inspectionRepository, VehicleChecklistService checklistService,
                             VehicleAvailabilityService availabilityService, DefectMapper defectMapper,
                             ApplicationEventPublisher eventPublisher) {
        this.vehicleRepository = vehicleRepository;
        this.tripRepository = tripRepository;
        this.inspectionRepository = inspectionRepository;
        this.checklistService = checklistService;
        this.availabilityService = availabilityService;
        this.defectMapper = defectMapper;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public DefectDto report(String vehicleIdRaw, RoadDefectRequest request, AuthenticatedUser driver) {
        UUID vehicleId = parseVehicleId(vehicleIdRaw);
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new VehicleNotFoundException(vehicleIdRaw));

        Trip trip = tripRepository.findFirstByVehicle_IdAndStatus(vehicleId, TripStatus.OPEN)
                .orElseThrow(() -> new VehicleStateConflictException("NO_OPEN_TRIP",
                        "El vehículo no tiene un viaje abierto: el problema se reporta en la inspección."));
        // Un viaje abierto antes de CAM-87 no sabe quién lo abrió: nadie puede decir que es suyo.
        if (trip.getOpenedById() == null || !trip.getOpenedById().equals(driver.id())) {
            throw new VehicleStateConflictException("NOT_YOUR_TRIP",
                    "Solo puede reportar el chofer que tiene el viaje abierto.");
        }

        ChecklistItemDef item = resolveItem(vehicleId, request == null ? null : request.itemId());
        ChecklistAnswerDto answer = new ChecklistAnswerDto(item.id(), CheckOutcome.DEFECT.toJson(), null,
                request.defect());
        // Las mismas reglas del defecto que en el checklist: título, largo, gravedad y foto si es bloqueante.
        // El control se pasa como obligatorio para que un defecto sin detalle no pase en silencio.
        ChecklistItemDef required = new ChecklistItemDef(item.id(), item.label(), item.type(), item.section(), true);
        InspectionValidator.ValidationOutcome outcome = validator.validate(InspectionType.ON_ROAD,
                List.of(required), List.of(answer));

        Instant now = Instant.now();
        Inspection inspection = new Inspection(trip, vehicleId, driver.id().toString(), driver.username(),
                InspectionType.ON_ROAD, now, null, null, outcome.hasBlockingDefect());
        InspectionAnswer answerEntity = new InspectionAnswer(item.id(), item.label(), CheckOutcome.DEFECT, null);
        String details = request.defect().details();
        Defect defect = new Defect(DefectSeverity.fromJson(request.defect().severity()),
                request.defect().description().trim(),
                details == null || details.isBlank() ? null : details.trim(),
                request.defect().photoUrl(), now);
        answerEntity.attachDefect(defect);
        inspection.addAnswer(answerEntity);
        inspectionRepository.save(inspection);

        // CAM-97 y CAM-102: lo mismo que un bloqueante del checklist. El viaje sigue abierto.
        if (defect.getSeverity() == DefectSeverity.BLOCKING) {
            eventPublisher.publishEvent(new BlockingDefectReportedEvent(vehicleId, vehicle.getPlate(),
                    InspectionType.ON_ROAD, driver.username(), List.of(defect.getDescription())));
            availabilityService.sync(vehicle, null);
        }
        // CAM-135: "otro problema" no es un control, así que no puede volverse recurrente.
        if (!ChecklistCatalog.ROAD_OTHER_ITEM.id().equals(item.id())) {
            eventPublisher.publishEvent(new DefectsReportedEvent(vehicleId, vehicle.getPlate(), List.of(item.id())));
        }

        return defectMapper.toDto(defect, vehicleId.toString(), vehicle.getPlate(), Map.of());
    }

    /** Los controles que se pueden elegir: los de tipo sí/no del pre-viaje de este vehículo, y "otro". */
    private List<ChecklistItemDef> reportableItems(UUID vehicleId) {
        List<ChecklistItemDef> items = new ArrayList<>(checklistService.resolve(vehicleId, InspectionType.PRE_TRIP)
                .stream()
                .filter(item -> item.type() == ChecklistItemType.CHECK)
                .toList());
        items.add(ChecklistCatalog.ROAD_OTHER_ITEM);
        return items;
    }

    private ChecklistItemDef resolveItem(UUID vehicleId, String itemId) {
        if (itemId == null || itemId.isBlank()) {
            throw invalidItem("Indicá qué falla.");
        }
        return reportableItems(vehicleId).stream()
                .filter(item -> item.id().equals(itemId))
                .findFirst()
                .orElseThrow(() -> invalidItem("No es un control del checklist de este vehículo."));
    }

    private static InspectionValidationException invalidItem(String message) {
        return new InspectionValidationException("El control elegido no es válido.",
                List.of(new ValidationErrorDetail("itemId", message)));
    }

    private UUID parseVehicleId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new VehicleNotFoundException(raw);
        }
    }
}
