package org.fleetguard.service;

import org.fleetguard.entity.NotificationChannel;
import org.fleetguard.entity.NotificationType;
import org.fleetguard.entity.Role;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Catálogo fijo de avisos (CAM-117): qué es cada uno, a quién le llega y su configuración por defecto.
 * OWNER: le llega a la persona involucrada (el técnico de la OT, el chofer del viaje); no se elige.
 */
public final class NotificationCatalog {

    public enum Audience {
        OWNER, CONFIGURABLE;

        public String toJson() {
            return this == OWNER ? "owner" : "configurable";
        }
    }

    public record Definition(NotificationType type, String label, String description, Audience audience,
                             String ownerDescription, boolean supportsLead) {

        public EffectiveNotificationSetting defaults() {
            return new EffectiveNotificationSetting(type, true, DEFAULT_CHANNELS,
                    audience == Audience.CONFIGURABLE ? Set.of(Role.ADMIN) : Set.of(), Set.of(),
                    supportsLead ? DEFAULT_LEAD_DAYS : null, supportsLead ? DEFAULT_LEAD_KM : null, false);
        }
    }

    // Lo que hacía la base de CAM-96/CAM-119 antes de que existiera esta configuración.
    static final Set<NotificationChannel> DEFAULT_CHANNELS = Set.of(NotificationChannel.IN_APP, NotificationChannel.PUSH);
    // Iguales al umbral de "por vencer" del dashboard (MaintenanceStatusCalculator), pero independientes.
    public static final int DEFAULT_LEAD_DAYS = 7;
    public static final int DEFAULT_LEAD_KM = 1000;

    private static final Map<NotificationType, Definition> DEFINITIONS = new EnumMap<>(NotificationType.class);

    static {
        add(NotificationType.BLOCKING_DEFECT_REPORTED, "Defecto bloqueante reportado",
                "Un chofer reporta un defecto que deja al vehículo sin poder salir.", Audience.CONFIGURABLE, null, false);
        add(NotificationType.WORK_ORDER_FINALIZED, "Orden de trabajo finalizada",
                "Se finaliza una orden de trabajo, interna o de un taller externo.", Audience.CONFIGURABLE, null, false);
        add(NotificationType.MAINTENANCE_DUE_SOON, "Mantenimiento por vencer",
                "Un plan asignado a un vehículo entra en la anticipación configurada.", Audience.CONFIGURABLE, null, true);
        add(NotificationType.MAINTENANCE_OVERDUE, "Mantenimiento vencido",
                "Un plan asignado a un vehículo se pasa de su fecha o de su kilometraje.", Audience.CONFIGURABLE, null, false);
        add(NotificationType.WORK_ORDER_ASSIGNED, "Orden de trabajo asignada",
                "A un técnico le asignan una orden de trabajo.", Audience.OWNER, "El técnico a cargo de la orden", false);
        add(NotificationType.WORK_ORDER_UNASSIGNED, "Orden de trabajo desasignada",
                "A un técnico le sacan una orden de trabajo que tenía.", Audience.OWNER, "El técnico que la tenía", false);
        add(NotificationType.WORK_ORDER_CANCELLED, "Orden de trabajo cancelada",
                "Se cancela una orden de trabajo que tenía un técnico.", Audience.OWNER, "El técnico que la tenía", false);
        add(NotificationType.TRIP_OPEN_REMINDER, "Recordatorio de viaje abierto",
                "El chofer no hizo el post-viaje y el viaje sigue abierto.", Audience.OWNER, "El chofer que abrió el viaje", false);
    }

    private NotificationCatalog() {
    }

    private static void add(NotificationType type, String label, String description, Audience audience,
                            String ownerDescription, boolean supportsLead) {
        DEFINITIONS.put(type, new Definition(type, label, description, audience, ownerDescription, supportsLead));
    }

    public static Definition of(NotificationType type) {
        return DEFINITIONS.get(type);
    }

    /** En el orden en que los muestra la pantalla de configuración. */
    public static List<Definition> all() {
        return List.copyOf(DEFINITIONS.values()).stream()
                .sorted((a, b) -> Integer.compare(order(a.type()), order(b.type())))
                .toList();
    }

    private static int order(NotificationType type) {
        return List.of(NotificationType.BLOCKING_DEFECT_REPORTED, NotificationType.WORK_ORDER_FINALIZED,
                NotificationType.MAINTENANCE_DUE_SOON, NotificationType.MAINTENANCE_OVERDUE,
                NotificationType.WORK_ORDER_ASSIGNED, NotificationType.WORK_ORDER_UNASSIGNED,
                NotificationType.WORK_ORDER_CANCELLED, NotificationType.TRIP_OPEN_REMINDER).indexOf(type);
    }
}
