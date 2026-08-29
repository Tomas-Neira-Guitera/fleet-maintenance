package org.example.checklist;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.example.checklist.ChecklistItemType.CHECK;
import static org.example.checklist.ChecklistItemType.NUMBER;
import static org.example.checklist.ChecklistSection.ACCESORIOS;
import static org.example.checklist.ChecklistSection.EXTERIOR;
import static org.example.checklist.ChecklistSection.INTERIOR;
import static org.example.checklist.ChecklistSection.POSTTRIP;

/**
 * Server-side mirror of fleet-maintenance-fe's src/checklist/checklistDefinitions.ts.
 * Keep this file in lockstep with that one: same itemIds, same required flags,
 * same accessory mapping -- the frontend already sends/expects these exact ids.
 *
 * The one field the item id "int-km" / "post-km" carries beyond the frontend's
 * definition is that the service layer (InspectionService) treats those two
 * ids specially to populate Inspection.odometerKm; that's a backend-only
 * convention, not something the catalog itself needs to express.
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

    public static final Map<AccessoryKey, ChecklistItemDef> ACCESSORY_CHECKLIST_ITEMS = new EnumMap<>(AccessoryKey.class);

    static {
        ACCESSORY_CHECKLIST_ITEMS.put(AccessoryKey.FAJA,
                new ChecklistItemDef("accessory-faja", "Estado y funcionamiento de la faja", CHECK, ACCESORIOS));
        ACCESSORY_CHECKLIST_ITEMS.put(AccessoryKey.TRACA,
                new ChecklistItemDef("accessory-traca", "Estado y funcionamiento de la traca", CHECK, ACCESORIOS));
        ACCESSORY_CHECKLIST_ITEMS.put(AccessoryKey.GRUA,
                new ChecklistItemDef("accessory-grua", "Estado y funcionamiento de la grúa", CHECK, ACCESORIOS));
        ACCESSORY_CHECKLIST_ITEMS.put(AccessoryKey.RAMPA,
                new ChecklistItemDef("accessory-rampa", "Estado y funcionamiento de la rampa hidráulica", CHECK, ACCESORIOS));
    }

    public static final List<ChecklistItemDef> POST_TRIP_ITEMS = List.of(
            new ChecklistItemDef("post-danos", "Daños nuevos en la carrocería", CHECK, POSTTRIP),
            new ChecklistItemDef("post-luces", "Luces", CHECK, POSTTRIP),
            new ChecklistItemDef("post-fugas", "Fugas visibles", CHECK, POSTTRIP),
            new ChecklistItemDef(ODOMETER_ITEM_ID_POST_TRIP, "Kilómetros finales", NUMBER, POSTTRIP, true)
    );

    private ChecklistCatalog() {
    }

    public static List<ChecklistItemDef> preTripItems(List<AccessoryKey> accessories) {
        List<ChecklistItemDef> items = new ArrayList<>(PRE_TRIP_EXTERIOR_ITEMS);
        items.addAll(PRE_TRIP_INTERIOR_BASE_ITEMS);
        for (AccessoryKey key : accessories) {
            ChecklistItemDef def = ACCESSORY_CHECKLIST_ITEMS.get(key);
            if (def != null) {
                items.add(def);
            }
        }
        return items;
    }

    public static List<ChecklistItemDef> postTripItems() {
        return POST_TRIP_ITEMS;
    }
}
