package org.fleetguard.entity.checklist;

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
        assertEquals(List.of("post-danos", "post-luces", "post-fugas", "post-km"), ids);
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
