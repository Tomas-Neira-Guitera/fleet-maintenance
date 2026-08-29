package org.example.checklist;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards that the server-side catalog stays in lockstep with fleet-maintenance-fe's checklistDefinitions.ts. */
class ChecklistCatalogTest {

    @Test
    void preTripBaseItemsMatchTheFrontendDefinitionsExactly() {
        List<ChecklistItemDef> items = ChecklistCatalog.preTripItems(List.of());
        List<String> ids = items.stream().map(ChecklistItemDef::id).toList();

        assertEquals(List.of("ext-luces", "ext-neumaticos", "ext-carroceria", "ext-fugas",
                "int-km", "int-documentacion", "int-testigos"), ids);

        assertTrue(items.stream().filter(i -> i.id().equals("int-km")).findFirst().orElseThrow().required());
        assertTrue(items.stream().filter(i -> !i.id().equals("int-km")).noneMatch(ChecklistItemDef::required));
    }

    @Test
    void accessoriesAppendExactlyOneItemEachInOrder() {
        List<ChecklistItemDef> items = ChecklistCatalog.preTripItems(
                List.of(AccessoryKey.FAJA, AccessoryKey.RAMPA));
        List<String> ids = items.stream().map(ChecklistItemDef::id).toList();

        assertEquals(9, ids.size());
        assertEquals("accessory-faja", ids.get(7));
        assertEquals("accessory-rampa", ids.get(8));
    }

    @Test
    void postTripItemsMatchTheFrontendDefinitionsExactly() {
        List<String> ids = ChecklistCatalog.postTripItems().stream().map(ChecklistItemDef::id).toList();
        assertEquals(List.of("post-danos", "post-luces", "post-fugas", "post-km"), ids);
    }
}
