package org.fleetguard.entity.checklist;

import org.fleetguard.entity.VehicleType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guarda que el catálogo server-side siga igual a checklistDefinitions.ts del frontend. */
class ChecklistCatalogTest {

    @Test
    void preTripBaseItemsMatchTheFrontendDefinitionsExactly() {
        List<ChecklistItemDef> items = ChecklistCatalog.preTripItems();
        List<String> ids = items.stream().map(ChecklistItemDef::id).toList();

        assertEquals(List.of("ext-luces", "ext-neumaticos", "ext-carroceria", "ext-fugas",
                "int-km", "int-documentacion", "int-testigos"), ids);

        assertTrue(items.stream().filter(i -> i.id().equals("int-km")).findFirst().orElseThrow().required());
        assertTrue(items.stream().filter(i -> !i.id().equals("int-km")).noneMatch(ChecklistItemDef::required));
    }

    @Test
    void postTripItemsMatchTheFrontendDefinitionsExactly() {
        List<String> ids = ChecklistCatalog.postTripItems().stream().map(ChecklistItemDef::id).toList();
        assertEquals(List.of("post-danos", "post-luces", "post-neumaticos", "post-fugas", "post-km"), ids);
    }

    @Test
    void elRemolqueTieneSuChecklistSinKmYConIdsPropios() {
        List<ChecklistItemDef> semi = ChecklistCatalog.trailerItems(VehicleType.SEMIRREMOLQUE_2_EJES);
        List<ChecklistItemDef> acoplado = ChecklistCatalog.trailerItems(VehicleType.ACOPLADO);

        assertTrue(semi.stream().allMatch(i -> i.id().startsWith("rem-") && !i.required()));
        assertTrue(semi.stream().anyMatch(i -> i.id().equals("rem-patas")));
        // El acoplado no tiene patas de apoyo y se engancha con la lanza.
        assertTrue(acoplado.stream().noneMatch(i -> i.id().equals("rem-patas")));
        assertEquals("Lanza y perno de enganche trabados", acoplado.get(0).label());
        assertEquals("Perno rey y quinta rueda trabados", semi.get(0).label());
        assertEquals(semi, ChecklistCatalog.preTripBaseItems(VehicleType.SEMIRREMOLQUE_2_EJES));
        assertEquals(ChecklistCatalog.preTripItems(), ChecklistCatalog.preTripBaseItems(VehicleType.TRACTOR_4X2));
        assertEquals(ChecklistCatalog.preTripItems(), ChecklistCatalog.preTripBaseItems(null));
    }

    @Test
    void elPostViajeDelRemolqueSeComparaConSuPreViaje() {
        assertEquals(ChecklistCatalog.trailerPostTripItems(), ChecklistCatalog.postTripItems(VehicleType.ACOPLADO));
        assertEquals(ChecklistCatalog.postTripItems(), ChecklistCatalog.postTripItems(VehicleType.TRACTOR_4X2));
        assertEquals("rem-luces", ChecklistCatalog.controlKey("rem-post-luces"));
        assertEquals("rem-neumaticos", ChecklistCatalog.controlKey("rem-post-neumaticos"));
        assertEquals("rem-enganche", ChecklistCatalog.controlKey("rem-post-enganche"));
    }

    // CAM-159: Neumáticos no se quita en ningún checklist, pero no es obligatorio como los km.
    @Test
    void neumaticosEsFijoEnTodosLosChecklists() {
        for (String id : List.of("ext-neumaticos", "post-neumaticos", "rem-neumaticos", "rem-post-neumaticos")) {
            ChecklistItemDef item = java.util.stream.Stream.of(ChecklistCatalog.preTripItems(), ChecklistCatalog.postTripItems(),
                            ChecklistCatalog.trailerItems(VehicleType.ACOPLADO), ChecklistCatalog.trailerPostTripItems())
                    .flatMap(List::stream).filter(i -> i.id().equals(id)).findFirst().orElseThrow();
            assertTrue(ChecklistCatalog.isLocked(item));
        }
        assertEquals("ext-neumaticos", ChecklistCatalog.controlKey("post-neumaticos"));
    }

    @Test
    void lucesYFugasDelPostViajeSonElMismoControlQueEnElPreViaje() {
        assertEquals("ext-luces", ChecklistCatalog.controlKey("post-luces"));
        assertEquals("ext-fugas", ChecklistCatalog.controlKey("post-fugas"));
        assertEquals("ext-luces", ChecklistCatalog.controlKey("ext-luces"));
        assertEquals("post-danos", ChecklistCatalog.controlKey("post-danos"));
        assertEquals("extra-123", ChecklistCatalog.controlKey("extra-123"));
        assertEquals(List.of("ext-luces", "post-luces"), ChecklistCatalog.itemIdsOfControl("ext-luces"));
        assertEquals(List.of("int-testigos"), ChecklistCatalog.itemIdsOfControl("int-testigos"));
    }
}
