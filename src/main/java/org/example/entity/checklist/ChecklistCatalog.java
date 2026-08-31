package org.example.entity.checklist;

import java.util.ArrayList;
import java.util.List;

import static org.example.entity.checklist.ChecklistItemType.CHECK;
import static org.example.entity.checklist.ChecklistItemType.NUMBER;
import static org.example.entity.checklist.ChecklistSection.EXTERIOR;
import static org.example.entity.checklist.ChecklistSection.INTERIOR;
import static org.example.entity.checklist.ChecklistSection.POSTTRIP;

/**
 * Espejo server-side de checklistDefinitions.ts del frontend. Mantener los
 * mismos itemIds y required flags que ese archivo.
 */
public final class ChecklistCatalog {

    public static final String ODOMETER_ITEM_ID_PRE_TRIP = "int-km";
    public static final String ODOMETER_ITEM_ID_POST_TRIP = "post-km";

    public static final List<ChecklistItemDef> PRE_TRIP_EXTERIOR_ITEMS = List.of(
            new ChecklistItemDef("ext-luces", "Luces", CHECK, EXTERIOR),
            new ChecklistItemDef("ext-neumaticos", "Neumáticos", CHECK, EXTERIOR),
            new ChecklistItemDef("ext-carroceria", "Carrocería, vidrios y espejos", CHECK, EXTERIOR),
            new ChecklistItemDef("ext-fugas", "Fugas visibles debajo del vehículo", CHECK, EXTERIOR)
    );

    public static final List<ChecklistItemDef> PRE_TRIP_INTERIOR_BASE_ITEMS = List.of(
            new ChecklistItemDef(ODOMETER_ITEM_ID_PRE_TRIP, "Kilómetros actuales", NUMBER, INTERIOR, true),
            new ChecklistItemDef("int-documentacion", "Documentación a bordo (seguro y VTV/RTO)", CHECK, INTERIOR),
            new ChecklistItemDef("int-testigos", "Testigos de tablero", CHECK, INTERIOR)
    );

    public static final List<ChecklistItemDef> POST_TRIP_ITEMS = List.of(
            new ChecklistItemDef("post-danos", "Daños nuevos en la carrocería", CHECK, POSTTRIP),
            new ChecklistItemDef("post-luces", "Luces", CHECK, POSTTRIP),
            new ChecklistItemDef("post-fugas", "Fugas visibles", CHECK, POSTTRIP),
            new ChecklistItemDef(ODOMETER_ITEM_ID_POST_TRIP, "Kilómetros finales", NUMBER, POSTTRIP, true)
    );

    private ChecklistCatalog() {
    }

    public static List<ChecklistItemDef> preTripItems() {
        List<ChecklistItemDef> items = new ArrayList<>(PRE_TRIP_EXTERIOR_ITEMS);
        items.addAll(PRE_TRIP_INTERIOR_BASE_ITEMS);
        return items;
    }

    public static List<ChecklistItemDef> postTripItems() {
        return POST_TRIP_ITEMS;
    }
}
