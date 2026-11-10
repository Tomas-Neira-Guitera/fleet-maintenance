package org.fleetguard.service;

import org.fleetguard.dto.ApplyChecklistTemplateRequest;
import org.fleetguard.dto.ApplyChecklistTemplateResultDto;
import org.fleetguard.dto.ChecklistTemplateDto;
import org.fleetguard.dto.ChecklistTemplateItemInput;
import org.fleetguard.dto.ChecklistTemplateRequest;
import org.fleetguard.entity.ChecklistTemplate;
import org.fleetguard.entity.InspectionType;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.VehicleChecklistItem;
import org.fleetguard.entity.checklist.ChecklistItemType;
import org.fleetguard.entity.checklist.ChecklistSection;
import org.fleetguard.exception.ChecklistTemplateNotFoundException;
import org.fleetguard.exception.VehicleNotFoundException;
import org.fleetguard.exception.VehicleStateConflictException;
import org.fleetguard.exception.VehicleValidationException;
import org.fleetguard.repository.ChecklistTemplateRepository;
import org.fleetguard.repository.VehicleChecklistItemRepository;
import org.fleetguard.repository.VehicleDisabledChecklistItemRepository;
import org.fleetguard.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// CAM-150: plantillas de checklist que se copian a varios vehículos.
class ChecklistTemplateServiceTest {

    private final ChecklistTemplateRepository templateRepository = mock(ChecklistTemplateRepository.class);
    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final VehicleChecklistItemRepository extraRepository = mock(VehicleChecklistItemRepository.class);
    private final VehicleChecklistService checklistService = new VehicleChecklistService(vehicleRepository,
            extraRepository, mock(VehicleDisabledChecklistItemRepository.class));
    private final ChecklistTemplateService service =
            new ChecklistTemplateService(templateRepository, vehicleRepository, checklistService);

    private final List<ChecklistTemplate> templates = new ArrayList<>();
    private final List<VehicleChecklistItem> extras = new ArrayList<>();
    private final List<Vehicle> vehicles = new ArrayList<>();

    private static void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    @BeforeEach
    void setUp() {
        when(templateRepository.save(any(ChecklistTemplate.class))).thenAnswer(inv -> {
            ChecklistTemplate template = inv.getArgument(0);
            if (template.getId() == null) {
                setId(template, UUID.randomUUID());
                templates.add(template);
            }
            return template;
        });
        when(templateRepository.findById(any())).thenAnswer(inv -> templates.stream()
                .filter(t -> t.getId().equals(inv.getArgument(0))).findFirst());
        when(templateRepository.existsById(any())).thenAnswer(inv -> templates.stream()
                .anyMatch(t -> t.getId().equals(inv.getArgument(0))));
        when(templateRepository.existsByNameIgnoreCase(any())).thenAnswer(inv -> templates.stream()
                .anyMatch(t -> t.getName().equalsIgnoreCase(inv.getArgument(0))));
        doAnswer(inv -> templates.remove(inv.<ChecklistTemplate>getArgument(0)))
                .when(templateRepository).delete(any(ChecklistTemplate.class));

        when(vehicleRepository.findById(any())).thenAnswer(inv -> vehicles.stream()
                .filter(v -> v.getId().equals(inv.getArgument(0))).findFirst());
        when(extraRepository.findByVehicleIdOrderByCreatedAtAsc(any())).thenAnswer(inv -> extras.stream()
                .filter(e -> e.getVehicleId().equals(inv.getArgument(0))).toList());
        when(extraRepository.findByVehicleIdAndActiveTrueOrderByCreatedAtAsc(any())).thenAnswer(inv -> extras.stream()
                .filter(e -> e.getVehicleId().equals(inv.getArgument(0)) && e.isActive()).toList());
        when(extraRepository.save(any(VehicleChecklistItem.class))).thenAnswer(inv -> {
            VehicleChecklistItem item = inv.getArgument(0);
            setId(item, UUID.randomUUID());
            extras.add(item);
            return item;
        });
    }

    private Vehicle vehicle(String plate, boolean active) throws Exception {
        Vehicle vehicle = new Vehicle(plate, "Iveco", "Daily");
        vehicle.setActive(active);
        setId(vehicle, UUID.randomUUID());
        vehicles.add(vehicle);
        return vehicle;
    }

    private static ChecklistTemplateRequest frigorifico() {
        return new ChecklistTemplateRequest("Frigorífico", List.of(
                new ChecklistTemplateItemInput("Temperatura de la cámara", "number", "interior"),
                new ChecklistTemplateItemInput("Equipo de frío", null, "exterior"),
                new ChecklistTemplateItemInput("Burletes de la puerta", "check", "exterior")));
    }

    private List<VehicleChecklistItem> extrasOf(Vehicle vehicle) {
        return extras.stream().filter(e -> e.getVehicleId().equals(vehicle.getId())).toList();
    }

    @Test
    void creaLaPlantillaConSusControlesEnOrden() {
        ChecklistTemplateDto created = service.create(frigorifico());

        assertEquals("Frigorífico", created.name());
        assertEquals(3, created.items().size());
        assertEquals("Temperatura de la cámara", created.items().get(0).label());
        assertEquals("number", created.items().get(0).type());
        assertEquals("check", created.items().get(1).type());
        assertEquals("exterior", created.items().get(1).section());
    }

    @Test
    void rechazaControlesInvalidosConElCampoDeCadaUno() {
        VehicleValidationException ex = assertThrows(VehicleValidationException.class,
                () -> service.create(new ChecklistTemplateRequest(" ", List.of(
                        new ChecklistTemplateItemInput("Temperatura", "texto", "interior"),
                        new ChecklistTemplateItemInput("Temperatura", "number", "posttrip"),
                        new ChecklistTemplateItemInput("Equipo de frío", null, "exterior"),
                        new ChecklistTemplateItemInput("equipo de FRIO", null, "exterior")))));

        List<String> fields = ex.getDetails().stream().map(d -> d.field()).toList();
        assertEquals(List.of("name", "items[0].type", "items[1].section", "items[3].label"), fields);
    }

    @Test
    void sinControlesNoSeCrea() {
        VehicleValidationException ex = assertThrows(VehicleValidationException.class,
                () -> service.create(new ChecklistTemplateRequest("Vacía", List.of())));
        assertEquals("items", ex.getDetails().get(0).field());
    }

    @Test
    void elNombreNoSeRepite() {
        service.create(frigorifico());

        VehicleStateConflictException ex = assertThrows(VehicleStateConflictException.class,
                () -> service.create(new ChecklistTemplateRequest("frigorífico",
                        List.of(new ChecklistTemplateItemInput("Otro", null, "interior")))));
        assertEquals("DUPLICATE_TEMPLATE_NAME", ex.getErrorCode());
    }

    @Test
    void aplicarCopiaLosControlesACadaVehiculo() throws Exception {
        ChecklistTemplateDto template = service.create(frigorifico());
        List<Vehicle> fleet = List.of(vehicle("AA111AA", true), vehicle("BB222BB", true), vehicle("CC333CC", true),
                vehicle("DD444DD", true), vehicle("EE555EE", true));

        ApplyChecklistTemplateResultDto result = service.apply(template.id(),
                new ApplyChecklistTemplateRequest(fleet.stream().map(v -> v.getId().toString()).toList()));

        assertEquals(15, result.added());
        assertEquals(0, result.skipped());
        assertEquals(5, result.vehicles().size());
        for (Vehicle vehicle : fleet) {
            List<VehicleChecklistItem> copied = extrasOf(vehicle);
            assertEquals(3, copied.size());
            assertTrue(copied.stream().allMatch(VehicleChecklistItem::isActive));
            // El chofer los ve en su pre-viaje.
            assertTrue(checklistService.resolve(vehicle.getId(), InspectionType.PRE_TRIP)
                    .stream().anyMatch(i -> i.label().equals("Temperatura de la cámara")));
        }
    }

    @Test
    void aplicarDosVecesNoDuplicaYLoDice() throws Exception {
        ChecklistTemplateDto template = service.create(frigorifico());
        Vehicle vehicle = vehicle("AA111AA", true);
        // Ya tenía uno de los controles, cargado a mano con otras mayúsculas y apagado.
        extras.add(new VehicleChecklistItem(vehicle.getId(), "EQUIPO DE FRIO", ChecklistItemType.CHECK,
                ChecklistSection.EXTERIOR, false));
        ApplyChecklistTemplateRequest request = new ApplyChecklistTemplateRequest(List.of(vehicle.getId().toString()));

        ApplyChecklistTemplateResultDto first = service.apply(template.id(), request);
        ApplyChecklistTemplateResultDto second = service.apply(template.id(), request);

        assertEquals(2, first.added());
        assertEquals(List.of("Equipo de frío"), first.vehicles().get(0).skippedLabels());
        assertEquals(0, second.added());
        assertEquals(3, second.skipped());
        assertEquals(3, extrasOf(vehicle).size());
    }

    @Test
    void unControlIgualAUnoDelCatalogoBaseSeSaltea() throws Exception {
        ChecklistTemplateDto template = service.create(new ChecklistTemplateRequest("Con luces", List.of(
                new ChecklistTemplateItemInput("luces", null, "exterior"),
                new ChecklistTemplateItemInput("Luces", null, "interior"))));
        Vehicle vehicle = vehicle("AA111AA", true);

        ApplyChecklistTemplateResultDto result = service.apply(template.id(),
                new ApplyChecklistTemplateRequest(List.of(vehicle.getId().toString())));

        assertEquals(1, result.added());
        assertEquals(List.of("luces"), result.vehicles().get(0).skippedLabels());
    }

    @Test
    void editarLaPlantillaNoCambiaLosVehiculos() throws Exception {
        ChecklistTemplateDto template = service.create(frigorifico());
        Vehicle vehicle = vehicle("AA111AA", true);
        service.apply(template.id(), new ApplyChecklistTemplateRequest(List.of(vehicle.getId().toString())));

        ChecklistTemplateDto edited = service.update(template.id(), new ChecklistTemplateRequest("Frigorífico v2",
                List.of(new ChecklistTemplateItemInput("Termómetro", "number", "interior"))));

        assertEquals("Frigorífico v2", edited.name());
        assertEquals(1, edited.items().size());
        assertEquals(List.of("Temperatura de la cámara", "Equipo de frío", "Burletes de la puerta"),
                extrasOf(vehicle).stream().map(VehicleChecklistItem::getLabel).toList());
    }

    @Test
    void borrarLaPlantillaDejaLosControlesCopiados() throws Exception {
        ChecklistTemplateDto template = service.create(frigorifico());
        Vehicle vehicle = vehicle("AA111AA", true);
        service.apply(template.id(), new ApplyChecklistTemplateRequest(List.of(vehicle.getId().toString())));

        service.delete(template.id());

        assertTrue(templates.isEmpty());
        assertEquals(3, extrasOf(vehicle).size());
        assertThrows(ChecklistTemplateNotFoundException.class, () -> service.get(template.id()));
    }

    @Test
    void unVehiculoDadoDeBajaNoSePuedeElegirYNoSeCopiaNada() throws Exception {
        ChecklistTemplateDto template = service.create(frigorifico());
        Vehicle active = vehicle("AA111AA", true);
        Vehicle inactive = vehicle("ZZ999ZZ", false);

        VehicleStateConflictException ex = assertThrows(VehicleStateConflictException.class,
                () -> service.apply(template.id(), new ApplyChecklistTemplateRequest(
                        List.of(active.getId().toString(), inactive.getId().toString()))));

        assertEquals("VEHICLE_INACTIVE", ex.getErrorCode());
        assertTrue(extras.isEmpty());
    }

    @Test
    void sinVehiculosOConUnoInexistenteFalla() {
        ChecklistTemplateDto template = service.create(frigorifico());

        assertThrows(VehicleValidationException.class,
                () -> service.apply(template.id(), new ApplyChecklistTemplateRequest(List.of())));
        assertThrows(VehicleNotFoundException.class, () -> service.apply(template.id(),
                new ApplyChecklistTemplateRequest(List.of(UUID.randomUUID().toString()))));
        assertThrows(ChecklistTemplateNotFoundException.class, () -> service.apply("no-existe",
                new ApplyChecklistTemplateRequest(List.of(UUID.randomUUID().toString()))));
    }

    @Test
    void elAltaConUnaPlantillaInexistenteSeRechaza() {
        VehicleValidationException ex = assertThrows(VehicleValidationException.class,
                () -> service.requireExists(UUID.randomUUID().toString(), "checklistTemplateId"));
        assertEquals("checklistTemplateId", ex.getDetails().get(0).field());
    }
}
