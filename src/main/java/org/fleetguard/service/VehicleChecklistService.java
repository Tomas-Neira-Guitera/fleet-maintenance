package org.fleetguard.service;

import org.fleetguard.dto.ChecklistItemDto;
import org.fleetguard.dto.CreateVehicleChecklistItemRequest;
import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.dto.UpdateVehicleChecklistItemRequest;
import org.fleetguard.dto.VehicleChecklistItemDto;
import org.fleetguard.entity.InspectionType;
import org.fleetguard.entity.VehicleChecklistItem;
import org.fleetguard.entity.VehicleDisabledChecklistItem;
import org.fleetguard.entity.checklist.ChecklistCatalog;
import org.fleetguard.entity.checklist.ChecklistItemDef;
import org.fleetguard.entity.checklist.ChecklistItemType;
import org.fleetguard.entity.checklist.ChecklistSection;
import org.fleetguard.exception.ChecklistItemNotFoundException;
import org.fleetguard.exception.VehicleNotFoundException;
import org.fleetguard.exception.VehicleStateConflictException;
import org.fleetguard.exception.VehicleValidationException;
import org.fleetguard.repository.VehicleChecklistItemRepository;
import org.fleetguard.repository.VehicleDisabledChecklistItemRepository;
import org.fleetguard.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CAM-31: checklist pre-trip por vehículo = catálogo base − ítems desactivados + ítems extra incluidos.
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

    /** Configuración para el admin: todos los ítems base y todos los extra, estén o no en el checklist. */
    public List<VehicleChecklistItemDto> getConfig(String vehicleIdRaw) {
        UUID vehicleId = requireVehicle(vehicleIdRaw);
        Set<String> disabled = disabledBaseIds(vehicleId);
        List<VehicleChecklistItemDto> result = new ArrayList<>();
        for (ChecklistItemDef base : ChecklistCatalog.preTripItems()) {
            boolean enabled = base.required() || !disabled.contains(base.id());
            result.add(new VehicleChecklistItemDto(base.id(), base.label(), base.type().toJson(),
                    base.section().toJson(), base.required(), "base", enabled, base.required()));
        }
        for (VehicleChecklistItem extra : extraRepository.findByVehicleIdOrderByCreatedAtAsc(vehicleId)) {
            result.add(toExtraDto(extra));
        }
        return result;
    }

    @Transactional
    public VehicleChecklistItemDto addExtra(String vehicleIdRaw, CreateVehicleChecklistItemRequest request) {
        UUID vehicleId = requireVehicle(vehicleIdRaw);
        List<FieldValidationErrorDetail> details = new ArrayList<>();
        ItemSpec item = validateItem(request.label(), request.type(), request.section(), "", details);
        if (!details.isEmpty()) {
            throw new VehicleValidationException("Datos inválidos para el ítem del checklist", details);
        }

        boolean enabled = request.enabled() == null || request.enabled();
        VehicleChecklistItem extra = extraRepository.save(
                new VehicleChecklistItem(vehicleId, item.label(), item.type(), item.section(), enabled));
        return toExtraDto(extra);
    }

    /** Un control propio ya validado. CAM-150 usa las mismas reglas para los de una plantilla. */
    public record ItemSpec(String label, ChecklistItemType type, ChecklistSection section) {
    }

    /** Lo que se copió a un vehículo y lo que se salteó porque ya tenía ese control. */
    public record CopyResult(int added, List<String> skippedLabels) {
    }

    /** Valida un control; los errores van a details con el prefijo de campo dado (ej. "items[2]."). Null si no es válido. */
    public static ItemSpec validateItem(String rawLabel, String rawType, String rawSection, String fieldPrefix,
                                        List<FieldValidationErrorDetail> details) {
        int before = details.size();
        String label = rawLabel == null ? "" : rawLabel.trim();
        if (label.isEmpty()) {
            details.add(new FieldValidationErrorDetail(fieldPrefix + "label", "Obligatorio"));
        } else if (label.length() > LABEL_MAX_LENGTH) {
            details.add(new FieldValidationErrorDetail(fieldPrefix + "label",
                    "No puede superar los " + LABEL_MAX_LENGTH + " caracteres"));
        }
        ChecklistItemType type = rawType == null ? ChecklistItemType.CHECK : ChecklistItemType.fromJson(rawType);
        if (type == null) {
            details.add(new FieldValidationErrorDetail(fieldPrefix + "type", "Debe ser 'check' o 'number'"));
        }
        ChecklistSection section = ChecklistSection.fromJson(rawSection);
        if (section != ChecklistSection.EXTERIOR && section != ChecklistSection.INTERIOR) {
            details.add(new FieldValidationErrorDetail(fieldPrefix + "section", "Debe ser 'exterior' o 'interior'"));
        }
        return details.size() == before ? new ItemSpec(label, type, section) : null;
    }

    /**
     * CAM-150: copia controles al vehículo como controles propios. Se saltea el que ya tiene (base o propio,
     * activo o no) con la misma etiqueta y sección, sin importar mayúsculas ni acentos.
     */
    @Transactional
    public CopyResult copyItems(UUID vehicleId, List<ItemSpec> items) {
        Set<String> existing = new HashSet<>();
        ChecklistCatalog.preTripItems().forEach(base -> existing.add(sameControlKey(base.label(), base.section())));
        extraRepository.findByVehicleIdOrderByCreatedAtAsc(vehicleId)
                .forEach(extra -> existing.add(sameControlKey(extra.getLabel(), extra.getSection())));

        int added = 0;
        List<String> skipped = new ArrayList<>();
        for (ItemSpec item : items) {
            if (!existing.add(sameControlKey(item.label(), item.section()))) {
                skipped.add(item.label());
                continue;
            }
            extraRepository.save(new VehicleChecklistItem(vehicleId, item.label(), item.type(), item.section()));
            added++;
        }
        return new CopyResult(added, skipped);
    }

    static String sameControlKey(String label, ChecklistSection section) {
        String plain = Normalizer.normalize(label.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
        return section.name() + "|" + plain;
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

    /** Elimina del vehículo un ítem extra. Las inspecciones viejas conservan su nombre (item_label). */
    @Transactional
    public void deleteExtra(String vehicleIdRaw, String itemId) {
        UUID vehicleId = requireVehicle(vehicleIdRaw);
        if (!itemId.startsWith(VehicleChecklistItem.ID_PREFIX)) {
            boolean base = ChecklistCatalog.preTripItems().stream().anyMatch(i -> i.id().equals(itemId));
            if (!base) {
                throw new ChecklistItemNotFoundException(itemId);
            }
            throw new VehicleStateConflictException("BASE_ITEM_NOT_DELETABLE",
                    "Los ítems del checklist base no se eliminan: se quitan del checklist de este vehículo");
        }
        extraRepository.delete(findExtra(vehicleId, itemId));
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
