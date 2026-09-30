package org.example.service;

import org.example.dto.CreateVehicleChecklistItemRequest;
import org.example.dto.UpdateVehicleChecklistItemRequest;
import org.example.dto.VehicleChecklistItemDto;
import org.example.entity.InspectionType;
import org.example.entity.VehicleChecklistItem;
import org.example.entity.VehicleDisabledChecklistItem;
import org.example.entity.checklist.ChecklistCatalog;
import org.example.entity.checklist.ChecklistItemDef;
import org.example.entity.checklist.ChecklistItemType;
import org.example.entity.checklist.ChecklistSection;
import org.example.exception.VehicleStateConflictException;
import org.example.exception.VehicleValidationException;
import org.example.repository.VehicleChecklistItemRepository;
import org.example.repository.VehicleDisabledChecklistItemRepository;
import org.example.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// CAM-31: checklist pre-trip por vehículo = base − desactivados + extras.
class VehicleChecklistServiceTest {

    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final VehicleChecklistItemRepository extraRepository = mock(VehicleChecklistItemRepository.class);
    private final VehicleDisabledChecklistItemRepository disabledRepository = mock(VehicleDisabledChecklistItemRepository.class);
    private final VehicleChecklistService service =
            new VehicleChecklistService(vehicleRepository, extraRepository, disabledRepository);

    private final UUID vehicleId = UUID.randomUUID();
    private final UUID otherVehicleId = UUID.randomUUID();
    private final List<VehicleChecklistItem> extras = new ArrayList<>();
    private final List<VehicleDisabledChecklistItem> disabled = new ArrayList<>();

    private static void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    @BeforeEach
    void setUp() {
        when(vehicleRepository.existsById(vehicleId)).thenReturn(true);
        when(vehicleRepository.existsById(otherVehicleId)).thenReturn(true);
        when(extraRepository.findByVehicleIdAndActiveTrueOrderByCreatedAtAsc(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return extras.stream().filter(e -> e.getVehicleId().equals(id) && e.isActive()).toList();
        });
        when(extraRepository.findById(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return extras.stream().filter(e -> id.equals(e.getId())).findFirst();
        });
        when(extraRepository.save(any(VehicleChecklistItem.class))).thenAnswer(inv -> {
            VehicleChecklistItem item = inv.getArgument(0);
            if (item.getId() == null) {
                setId(item, UUID.randomUUID());
                extras.add(item);
            }
            return item;
        });
        when(disabledRepository.findByVehicleId(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return disabled.stream().filter(d -> d.getVehicleId().equals(id)).toList();
        });
        when(disabledRepository.findByVehicleIdAndBaseItemId(any(), anyString())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            String itemId = inv.getArgument(1);
            return disabled.stream()
                    .filter(d -> d.getVehicleId().equals(id) && d.getBaseItemId().equals(itemId))
                    .findFirst();
        });
        when(disabledRepository.save(any(VehicleDisabledChecklistItem.class))).thenAnswer(inv -> {
            VehicleDisabledChecklistItem row = inv.getArgument(0);
            disabled.add(row);
            return row;
        });
        doAnswer(inv -> disabled.remove(inv.<VehicleDisabledChecklistItem>getArgument(0)))
                .when(disabledRepository).delete(any(VehicleDisabledChecklistItem.class));
    }

    private List<String> preTripIds(UUID vehicle) {
        return service.resolve(vehicle, InspectionType.PRE_TRIP).stream().map(ChecklistItemDef::id).toList();
    }

    @Test
    void withoutConfigurationTheChecklistIsTheBaseCatalog() {
        assertEquals(ChecklistCatalog.preTripItems(), service.resolve(vehicleId, InspectionType.PRE_TRIP));
    }

    @Test
    void extraItemAppearsOnlyForItsVehicle() {
        VehicleChecklistItemDto created = service.addExtra(vehicleId.toString(),
                new CreateVehicleChecklistItemRequest("Faja de sujeción", "check", "exterior"));

        assertTrue(created.id().startsWith(VehicleChecklistItem.ID_PREFIX));
        assertTrue(preTripIds(vehicleId).contains(created.id()));
        assertFalse(preTripIds(otherVehicleId).contains(created.id()));
    }

    @Test
    void extraItemIsPlacedInItsSection() {
        VehicleChecklistItemDto created = service.addExtra(vehicleId.toString(),
                new CreateVehicleChecklistItemRequest("Estado de la grúa", "check", "exterior"));

        List<ChecklistItemDef> items = service.resolve(vehicleId, InspectionType.PRE_TRIP);
        int extraIndex = items.stream().map(ChecklistItemDef::id).toList().indexOf(created.id());
        assertEquals(ChecklistSection.EXTERIOR, items.get(extraIndex).section());
        assertEquals(ChecklistSection.INTERIOR, items.get(extraIndex + 1).section());
    }

    @Test
    void disabledBaseItemDisappearsAndCanBeReEnabled() {
        service.setEnabled(vehicleId.toString(), "ext-fugas", new UpdateVehicleChecklistItemRequest(false));
        assertFalse(preTripIds(vehicleId).contains("ext-fugas"));
        assertTrue(preTripIds(otherVehicleId).contains("ext-fugas"));

        service.setEnabled(vehicleId.toString(), "ext-fugas", new UpdateVehicleChecklistItemRequest(true));
        assertTrue(preTripIds(vehicleId).contains("ext-fugas"));
    }

    @Test
    void removedExtraItemIsSoftDeleted() {
        VehicleChecklistItemDto created = service.addExtra(vehicleId.toString(),
                new CreateVehicleChecklistItemRequest("Traca", "check", "exterior"));

        service.setEnabled(vehicleId.toString(), created.id(), new UpdateVehicleChecklistItemRequest(false));

        assertFalse(preTripIds(vehicleId).contains(created.id()));
        assertEquals(1, extras.size());
        assertFalse(extras.get(0).isActive());
    }

    @Test
    void requiredItemsCannotBeRemoved() {
        VehicleStateConflictException ex = assertThrows(VehicleStateConflictException.class,
                () -> service.setEnabled(vehicleId.toString(), ChecklistCatalog.ODOMETER_ITEM_ID_PRE_TRIP,
                        new UpdateVehicleChecklistItemRequest(false)));

        assertEquals("ITEM_LOCKED", ex.getErrorCode());
        verify(disabledRepository, never()).save(any());
    }

    @Test
    void extraItemRequiresLabelTypeAndPreTripSection() {
        VehicleValidationException ex = assertThrows(VehicleValidationException.class,
                () -> service.addExtra(vehicleId.toString(), new CreateVehicleChecklistItemRequest(" ", "texto", "posttrip")));

        assertEquals(3, ex.getDetails().size());
    }

    @Test
    void postTripChecklistIsNotConfigurable() throws Exception {
        VehicleChecklistItem extra = new VehicleChecklistItem(vehicleId, "Rampa", ChecklistItemType.CHECK, ChecklistSection.EXTERIOR);
        setId(extra, UUID.randomUUID());
        extras.add(extra);

        assertEquals(ChecklistCatalog.postTripItems(), service.resolve(vehicleId, InspectionType.POST_TRIP));
    }
}
