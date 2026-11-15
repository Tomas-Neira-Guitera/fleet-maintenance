package org.fleetguard.entity.checklist;

import org.fleetguard.entity.VehicleType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.fleetguard.entity.checklist.ChecklistItemType.CHECK;
import static org.fleetguard.entity.checklist.ChecklistItemType.NUMBER;
import static org.fleetguard.entity.checklist.ChecklistSection.EXTERIOR;
import static org.fleetguard.entity.checklist.ChecklistSection.INTERIOR;
import static org.fleetguard.entity.checklist.ChecklistSection.POSTTRIP;

/**
 * Espejo server-side de checklistDefinitions.ts del frontend. Mantener los
 * mismos itemIds y required flags que ese archivo.
 */
public final class ChecklistCatalog {

    public static final String ODOMETER_ITEM_ID_PRE_TRIP = "int-km";
    public static final String ODOMETER_ITEM_ID_POST_TRIP = "post-km";

    /**
     * CAM-139: lo que el chofer elige al reportar en viaje un problema que no es ningún control del
     * checklist. No cuenta para los defectos recurrentes: "otro" no es un control.
     */
    public static final ChecklistItemDef ROAD_OTHER_ITEM =
            new ChecklistItemDef("road-otro", "Otro problema", CHECK, INTERIOR);

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

    /**
     * CAM-159: el remolque se revisa en el pre-viaje de lo que lo arrastra. Ids propios ("rem-"), para que sus
     * respuestas y errores no se confundan con los del camión en el mismo envío.
     */
    public static final String TRAILER_TIRES_ITEM_ID = "rem-neumaticos";
    public static final String TRAILER_LANDING_GEAR_ITEM_ID = "rem-patas";

    private static final List<ChecklistItemDef> TRAILER_ITEMS = List.of(
            new ChecklistItemDef("rem-enganche", "Enganche trabado", CHECK, EXTERIOR),
            new ChecklistItemDef("rem-aire", "Mangueras de aire y cable de luces conectados", CHECK, EXTERIOR),
            new ChecklistItemDef("rem-luces", "Luces, patente y reflectivos", CHECK, EXTERIOR),
            new ChecklistItemDef(TRAILER_TIRES_ITEM_ID, "Neumáticos", CHECK, EXTERIOR),
            new ChecklistItemDef("rem-frenos", "Frenos", CHECK, EXTERIOR),
            new ChecklistItemDef(TRAILER_LANDING_GEAR_ITEM_ID, "Patas de apoyo levantadas", CHECK, EXTERIOR),
            new ChecklistItemDef("rem-carga", "Puertas, lona y sujeción de la carga", CHECK, EXTERIOR),
            new ChecklistItemDef("rem-paragolpes", "Paragolpes trasero", CHECK, EXTERIOR)
    );

    // CAM-144: controles del post-viaje que son el mismo control que uno del pre-viaje.
    private static final Map<String, String> SAME_CONTROL_IN_PRE_TRIP = Map.of(
            "post-luces", "ext-luces",
            "post-fugas", "ext-fugas"
    );

    private ChecklistCatalog() {
    }

    /** La clave del control: el id del pre-viaje si el ítem tiene uno equivalente, si no el mismo id. */
    public static String controlKey(String itemId) {
        return SAME_CONTROL_IN_PRE_TRIP.getOrDefault(itemId, itemId);
    }

    /** Todos los itemIds que son ese control (la clave y sus equivalentes del post-viaje). */
    public static List<String> itemIdsOfControl(String controlKey) {
        return Stream.concat(Stream.of(controlKey), SAME_CONTROL_IN_PRE_TRIP.entrySet().stream()
                        .filter(entry -> entry.getValue().equals(controlKey))
                        .map(Map.Entry::getKey))
                .sorted()
                .toList();
    }

    public static List<ChecklistItemDef> preTripItems() {
        List<ChecklistItemDef> items = new ArrayList<>(PRE_TRIP_EXTERIOR_ITEMS);
        items.addAll(PRE_TRIP_INTERIOR_BASE_ITEMS);
        return items;
    }

    public static List<ChecklistItemDef> postTripItems() {
        return POST_TRIP_ITEMS;
    }

    /** CAM-159: el checklist base de un remolque. El acoplado no tiene patas de apoyo y se engancha con la lanza. */
    public static List<ChecklistItemDef> trailerItems(VehicleType type) {
        boolean fullTrailer = type != null && type.kind() == VehicleType.Kind.FULL_TRAILER;
        return TRAILER_ITEMS.stream()
                .filter(i -> !(fullTrailer && i.id().equals(TRAILER_LANDING_GEAR_ITEM_ID)))
                .map(i -> !i.id().equals("rem-enganche") ? i : new ChecklistItemDef(i.id(),
                        fullTrailer ? "Lanza y perno de enganche trabados" : "Perno rey y quinta rueda trabados",
                        i.type(), i.section()))
                .toList();
    }

    /** El checklist base del pre-viaje del vehículo: el de un remolque o el de siempre. */
    public static List<ChecklistItemDef> preTripBaseItems(VehicleType type) {
        return type != null && type.isTrailer() ? trailerItems(type) : preTripItems();
    }
}
