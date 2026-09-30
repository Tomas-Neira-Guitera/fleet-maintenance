package org.example.service;

import org.example.dto.ChecklistItemDto;
import org.example.dto.CreateVehicleChecklistItemRequest;
import org.example.dto.FieldValidationErrorDetail;
import org.example.dto.UpdateVehicleChecklistItemRequest;
import org.example.dto.VehicleChecklistItemDto;
import org.example.entity.InspectionType;
import org.example.entity.VehicleChecklistItem;
import org.example.entity.VehicleDisabledChecklistItem;
import org.example.entity.checklist.ChecklistCatalog;
import org.example.entity.checklist.ChecklistItemDef;
import org.example.entity.checklist.ChecklistItemType;
import org.example.entity.checklist.ChecklistSection;
import org.example.exception.ChecklistItemNotFoundException;
import org.example.exception.VehicleNotFoundException;
import org.example.exception.VehicleStateConflictException;
import org.example.exception.VehicleValidationException;
import org.example.repository.VehicleChecklistItemRepository;
import org.example.repository.VehicleDisabledChecklistItemRepository;
import org.example.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CAM-31: checklist pre-trip por vehículo = catálogo base − ítems desactivados + ítems extra.
 * El post-trip sigue siendo la lista fija acotada. Los ítems obligatorios (km) no se pueden quitar.
 */
@Service
public class VehicleChecklistService {

    public static final int LABEL_MAX_LENGTH = 60;

    private final VehicleRepository vehicleRepository;
    private final VehicleChecklistItemRepository extraRepository;
    private final VehicleDisabledChecklistItemRepository disabledRepository;

    public VehicleChecklistService(VehicleRepository vehicleRepository,
                                   VehicleChecklistItemRepository extraRepository,
                                   VehicleDisabledChecklistItemRepository disabledRepository) {
        this.vehicleRepository = vehicleRepository;
        this.extraRepository = extraRepository;
        this.disabledRepository = disabledRepository;
    }

    /** Checklist efectivo de un vehículo, en el orden en que lo ve el chofer. */
    public List<ChecklistItemDef> resolve(UUID vehicleId, InspectionType type) {
        if (type != InspectionType.PRE_TRIP) {
            return ChecklistCatalog.postTripItems();
        }
        Set<String> disabled = disabledBaseIds(vehicleId);
        List<VehicleChecklistItem> extras = extraRepository.findByVehicleIdAndActiveTrueOrderByCreatedAtAsc(vehicleId);

        List<ChecklistItemDef> items = new ArrayList<>();
        for (ChecklistSection section : List.of(ChecklistSection.EXTERIOR, ChecklistSection.INTERIOR)) {
            ChecklistCatalog.preTripItems().stream()
                    .filter(i -> i.section() == section)
                    .filter(i -> i.required() || !disabled.contains(i.id()))
                    .forEach(items::add);
            extras.stream()
                    .filter(e -> e.getSection() == section)
                    .map(VehicleChecklistService::toDef)
                    .forEach(items::add);
        }
        return items;
    }

    public List<ChecklistItemDto> getChecklist(String vehicleIdRaw, String typeRaw) {
        UUID vehicleId = requireVehicle(vehicleIdRaw);
        InspectionType type = typeRaw == null ? InspectionType.PRE_TRIP : InspectionType.fromJson(typeRaw);
        if (type == null) {
            throw new VehicleValidationException("Tipo de inspección inválido",
                    List.of(new FieldValidationErrorDetail("type", "Debe ser 'pre-trip' o 'post-trip'")));
        }
        return resolve(vehicleId, type).stream()
                .map(i -> new ChecklistItemDto(i.id(), i.label(), i.type().toJson(), i.section().toJson(), i.required()))
                .toList();
    }

    /** Configuración para el admin: todos los ítems base (activos o no) + los extra vigentes. */
    public List<VehicleChecklistItemDto> getConfig(String vehicleIdRaw) {
        UUID vehicleId = requireVehicle(vehicleIdRaw);
        Set<String> disabled = disabledBaseIds(vehicleId);
        List<VehicleChecklistItemDto> result = new ArrayList<>();
        for (ChecklistItemDef base : ChecklistCatalog.preTripItems()) {
            boolean enabled = base.required() || !disabled.contains(base.id());
            result.add(new VehicleChecklistItemDto(base.id(), base.label(), base.type().toJson(),
                    base.section().toJson(), base.required(), "base", enabled, base.required()));
        }
        for (VehicleChecklistItem extra : extraRepository.findByVehicleIdAndActiveTrueOrderByCreatedAtAsc(vehicleId)) {
            result.add(toExtraDto(extra));
        }
        return result;
    }

    @Transactional
    public VehicleChecklistItemDto addExtra(String vehicleIdRaw, CreateVehicleChecklistItemRequest request) {
        UUID vehicleId = requireVehicle(vehicleIdRaw);
        List<FieldValidationErrorDetail> details = new ArrayList<>();
        String label = request.label() == null ? "" : request.label().trim();
        if (label.isEmpty()) {
            details.add(new FieldValidationErrorDetail("label", "Obligatorio"));
        } else if (label.length() > LABEL_MAX_LENGTH) {
            details.add(new FieldValidationErrorDetail("label", "No puede superar los " + LABEL_MAX_LENGTH + " caracteres"));
        }
        ChecklistItemType type = ChecklistItemType.fromJson(request.type());
        if (type == null) {
            details.add(new FieldValidationErrorDetail("type", "Debe ser 'check' o 'number'"));
        }
        ChecklistSection section = ChecklistSection.fromJson(request.section());
        if (section != ChecklistSection.EXTERIOR && section != ChecklistSection.INTERIOR) {
            details.add(new FieldValidationErrorDetail("section", "Debe ser 'exterior' o 'interior'"));
        }
        if (!details.isEmpty()) {
            throw new VehicleValidationException("Datos inválidos para el ítem del checklist", details);
        }

        VehicleChecklistItem extra = extraRepository.save(new VehicleChecklistItem(vehicleId, label, type, section));
        return toExtraDto(extra);
    }

    @Transactional
    public VehicleChecklistItemDto setEnabled(String vehicleIdRaw, String itemId, UpdateVehicleChecklistItemRequest request) {
        UUID vehicleId = requireVehicle(vehicleIdRaw);
        if (request.enabled() == null) {
            throw new VehicleValidationException("Datos inválidos",
                    List.of(new FieldValidationErrorDetail("enabled", "Obligatorio")));
        }
        boolean enabled = request.enabled();

        if (itemId.startsWith(VehicleChecklistItem.ID_PREFIX)) {
            VehicleChecklistItem extra = findExtra(vehicleId, itemId);
            extra.setActive(enabled);
            extraRepository.save(extra);
            return toExtraDto(extra);
        }

        ChecklistItemDef base = ChecklistCatalog.preTripItems().stream()
                .filter(i -> i.id().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ChecklistItemNotFoundException(itemId));
        if (base.required()) {
            throw new VehicleStateConflictException("ITEM_LOCKED",
                    "\"" + base.label() + "\" es obligatorio y no se puede quitar del checklist");
        }
        Optional<VehicleDisabledChecklistItem> row = disabledRepository.findByVehicleIdAndBaseItemId(vehicleId, itemId);
        if (enabled) {
            row.ifPresent(disabledRepository::delete);
        } else if (row.isEmpty()) {
            disabledRepository.save(new VehicleDisabledChecklistItem(vehicleId, itemId));
        }
        return new VehicleChecklistItemDto(base.id(), base.label(), base.type().toJson(), base.section().toJson(),
                false, "base", enabled, false);
    }

    private VehicleChecklistItem findExtra(UUID vehicleId, String itemId) {
        UUID id;
        try {
            id = UUID.fromString(itemId.substring(VehicleChecklistItem.ID_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            throw new ChecklistItemNotFoundException(itemId);
        }
        return extraRepository.findById(id)
                .filter(e -> e.getVehicleId().equals(vehicleId))
                .orElseThrow(() -> new ChecklistItemNotFoundException(itemId));
    }

    private Set<String> disabledBaseIds(UUID vehicleId) {
        return disabledRepository.findByVehicleId(vehicleId).stream()
                .map(VehicleDisabledChecklistItem::getBaseItemId)
                .collect(Collectors.toSet());
    }

    private UUID requireVehicle(String raw) {
        UUID id;
        try {
            id = UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new VehicleNotFoundException(raw);
        }
        if (!vehicleRepository.existsById(id)) {
            throw new VehicleNotFoundException(raw);
        }
        return id;
    }

    private static ChecklistItemDef toDef(VehicleChecklistItem extra) {
        return new ChecklistItemDef(extra.getItemId(), extra.getLabel(), extra.getType(), extra.getSection());
    }

    private static VehicleChecklistItemDto toExtraDto(VehicleChecklistItem extra) {
        return new VehicleChecklistItemDto(extra.getItemId(), extra.getLabel(), extra.getType().toJson(),
                extra.getSection().toJson(), false, "extra", extra.isActive(), false);
    }
}
