package org.fleetguard.service;

import org.fleetguard.dto.ApplyChecklistTemplateRequest;
import org.fleetguard.dto.ApplyChecklistTemplateResultDto;
import org.fleetguard.dto.ChecklistTemplateDto;
import org.fleetguard.dto.ChecklistTemplateItemInput;
import org.fleetguard.dto.ChecklistTemplateRequest;
import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.entity.ChecklistTemplate;
import org.fleetguard.entity.ChecklistTemplateItem;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.exception.ChecklistTemplateNotFoundException;
import org.fleetguard.exception.VehicleNotFoundException;
import org.fleetguard.exception.VehicleStateConflictException;
import org.fleetguard.exception.VehicleValidationException;
import org.fleetguard.repository.ChecklistTemplateRepository;
import org.fleetguard.repository.VehicleRepository;
import org.fleetguard.service.VehicleChecklistService.CopyResult;
import org.fleetguard.service.VehicleChecklistService.ItemSpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * CAM-150: plantillas de checklist. Aplicar una plantilla copia sus controles a cada vehículo como
 * controles propios (VehicleChecklistService.copyItems); el vehículo no queda vinculado a la plantilla.
 */
@Service
public class ChecklistTemplateService {

    public static final int NAME_MAX_LENGTH = 60;
    public static final int MAX_ITEMS = 30;
    public static final int MAX_VEHICLES = 200;

    private final ChecklistTemplateRepository templateRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleChecklistService checklistService;

    public ChecklistTemplateService(ChecklistTemplateRepository templateRepository, VehicleRepository vehicleRepository,
                                    VehicleChecklistService checklistService) {
        this.templateRepository = templateRepository;
        this.vehicleRepository = vehicleRepository;
        this.checklistService = checklistService;
    }

    @Transactional(readOnly = true)
    public List<ChecklistTemplateDto> list() {
        return templateRepository.findAllByOrderByNameAsc().stream().map(ChecklistTemplateService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ChecklistTemplateDto get(String id) {
        return toDto(find(id));
    }

    @Transactional
    public ChecklistTemplateDto create(ChecklistTemplateRequest request) {
        Valid valid = validate(request);
        if (templateRepository.existsByNameIgnoreCase(valid.name())) {
            throw duplicateName(valid.name());
        }
        ChecklistTemplate template = new ChecklistTemplate(valid.name());
        replaceItems(template, valid.items());
        return toDto(templateRepository.save(template));
    }

    /** Reemplaza nombre y controles. Los vehículos donde ya se aplicó no cambian. */
    @Transactional
    public ChecklistTemplateDto update(String id, ChecklistTemplateRequest request) {
        ChecklistTemplate template = find(id);
        Valid valid = validate(request);
        if (templateRepository.existsByNameIgnoreCaseAndIdNot(valid.name(), template.getId())) {
            throw duplicateName(valid.name());
        }
        template.setName(valid.name());
        replaceItems(template, valid.items());
        return toDto(templateRepository.save(template));
    }

    /** Los controles ya copiados quedan en los vehículos y las inspecciones viejas no cambian. */
    @Transactional
    public void delete(String id) {
        templateRepository.delete(find(id));
    }

    @Transactional
    public ApplyChecklistTemplateResultDto apply(String id, ApplyChecklistTemplateRequest request) {
        ChecklistTemplate template = find(id);
        List<Vehicle> vehicles = requireActiveVehicles(request == null ? null : request.vehicleIds());
        List<ItemSpec> items = specsOf(template);

        int added = 0;
        int skipped = 0;
        List<ApplyChecklistTemplateResultDto.VehicleResult> results = new ArrayList<>();
        for (Vehicle vehicle : vehicles) {
            CopyResult copy = checklistService.copyItems(vehicle.getId(), items);
            added += copy.added();
            skipped += copy.skippedLabels().size();
            results.add(new ApplyChecklistTemplateResultDto.VehicleResult(vehicle.getId().toString(), vehicle.getPlate(),
                    copy.added(), copy.skippedLabels().size(), copy.skippedLabels()));
        }
        return new ApplyChecklistTemplateResultDto(template.getId().toString(), added, skipped, results);
    }

    /** CAM-150: el alta de un vehículo con plantilla. Se valida antes de crear el vehículo. */
    public void requireExists(String id, String field) {
        if (Uuids.parse(id).map(templateRepository::existsById).orElse(false)) {
            return;
        }
        throw new VehicleValidationException("Datos inválidos para crear el vehículo",
                List.of(new FieldValidationErrorDetail(field, "No existe la plantilla de checklist")));
    }

    @Transactional
    public CopyResult applyToNewVehicle(String id, UUID vehicleId) {
        return checklistService.copyItems(vehicleId, specsOf(find(id)));
    }

    private record Valid(String name, List<ItemSpec> items) {
    }

    private static Valid validate(ChecklistTemplateRequest request) {
        List<FieldValidationErrorDetail> details = new ArrayList<>();
        String name = request == null || request.name() == null ? "" : request.name().trim();
        if (name.isEmpty()) {
            details.add(new FieldValidationErrorDetail("name", "Obligatorio"));
        } else if (name.length() > NAME_MAX_LENGTH) {
            details.add(new FieldValidationErrorDetail("name", "No puede superar los " + NAME_MAX_LENGTH + " caracteres"));
        }

        List<ChecklistTemplateItemInput> inputs = request == null || request.items() == null ? List.of() : request.items();
        if (inputs.isEmpty()) {
            details.add(new FieldValidationErrorDetail("items", "La plantilla tiene que tener al menos un control"));
        } else if (inputs.size() > MAX_ITEMS) {
            details.add(new FieldValidationErrorDetail("items", "Hasta " + MAX_ITEMS + " controles por plantilla"));
        }

        List<ItemSpec> items = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < inputs.size() && i < MAX_ITEMS; i++) {
            ChecklistTemplateItemInput input = inputs.get(i);
            String prefix = "items[" + i + "].";
            ItemSpec item = input == null
                    ? VehicleChecklistService.validateItem(null, null, null, prefix, details)
                    : VehicleChecklistService.validateItem(input.label(), input.type(), input.section(), prefix, details);
            if (item == null) {
                continue;
            }
            if (!keys.add(VehicleChecklistService.sameControlKey(item.label(), item.section()))) {
                details.add(new FieldValidationErrorDetail(prefix + "label", "Ya hay un control con ese nombre en la misma sección"));
                continue;
            }
            items.add(item);
        }
        if (!details.isEmpty()) {
            throw new VehicleValidationException("Datos inválidos para la plantilla", details);
        }
        return new Valid(name, items);
    }

    private List<Vehicle> requireActiveVehicles(List<String> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            throw new VehicleValidationException("Datos inválidos",
                    List.of(new FieldValidationErrorDetail("vehicleIds", "Elegí al menos un vehículo")));
        }
        Set<String> unique = new LinkedHashSet<>(rawIds);
        if (unique.size() > MAX_VEHICLES) {
            throw new VehicleValidationException("Datos inválidos",
                    List.of(new FieldValidationErrorDetail("vehicleIds", "Hasta " + MAX_VEHICLES + " vehículos por vez")));
        }
        List<Vehicle> vehicles = new ArrayList<>();
        for (String raw : unique) {
            Vehicle vehicle = Uuids.parse(raw).flatMap(vehicleRepository::findById)
                    .orElseThrow(() -> new VehicleNotFoundException(raw));
            if (!vehicle.isActive()) {
                throw new VehicleStateConflictException("VEHICLE_INACTIVE",
                        vehicle.getPlate() + " está dado de baja: no se le puede aplicar una plantilla");
            }
            vehicles.add(vehicle);
        }
        return vehicles;
    }

    private static void replaceItems(ChecklistTemplate template, List<ItemSpec> items) {
        template.getItems().clear();
        for (int i = 0; i < items.size(); i++) {
            ItemSpec item = items.get(i);
            template.getItems().add(new ChecklistTemplateItem(template, item.label(), item.type(), item.section(), i));
        }
    }

    private static List<ItemSpec> specsOf(ChecklistTemplate template) {
        return template.getItems().stream()
                .map(i -> new ItemSpec(i.getLabel(), i.getType(), i.getSection()))
                .toList();
    }

    private ChecklistTemplate find(String id) {
        return Uuids.parse(id).flatMap(templateRepository::findById)
                .orElseThrow(() -> new ChecklistTemplateNotFoundException(id));
    }

    private static VehicleStateConflictException duplicateName(String name) {
        return new VehicleStateConflictException("DUPLICATE_TEMPLATE_NAME", "Ya existe una plantilla llamada \"" + name + "\"");
    }

    private static ChecklistTemplateDto toDto(ChecklistTemplate template) {
        return new ChecklistTemplateDto(template.getId().toString(), template.getName(),
                template.getItems().stream()
                        .map(i -> new ChecklistTemplateDto.Item(i.getId() == null ? null : i.getId().toString(),
                                i.getLabel(), i.getType().toJson(), i.getSection().toJson()))
                        .toList(),
                template.getCreatedAt().toString());
    }
}
