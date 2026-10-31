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

    /** Qué anticipación se configura: ninguna, solo días (documentos, CAM-127) o días y km (planes). */
    public enum Lead {
        NONE, DAYS, DAYS_AND_KM
    }

    public record Definition(NotificationType type, String label, String description, Audience audience,
                             String ownerDescription, Lead lead, Integer defaultLeadDays) {

        public boolean supportsLead() {
            return lead != Lead.NONE;
        }

        public boolean supportsLeadKm() {
            return lead == Lead.DAYS_AND_KM;
        }

        public EffectiveNotificationSetting defaults() {
            return new EffectiveNotificationSetting(type, true, DEFAULT_CHANNELS,
                    audience == Audience.CONFIGURABLE ? Set.of(Role.ADMIN) : Set.of(), Set.of(),
                    supportsLead() ? defaultLeadDays : null, supportsLeadKm() ? DEFAULT_LEAD_KM : null, false);
        }
    }

    // Lo que hacía la base de CAM-96/CAM-119 antes de que existiera esta configuración.
    static final Set<NotificationChannel> DEFAULT_CHANNELS = Set.of(NotificationChannel.IN_APP, NotificationChannel.PUSH);
    // Iguales al umbral de "por vencer" del dashboard (MaintenanceStatusCalculator), pero independientes.
    public static final int DEFAULT_LEAD_DAYS = 7;
    public static final int DEFAULT_LEAD_KM = 1000;
    // CAM-127: los documentos se renuevan con más tiempo (turno de VTV, cotizar el seguro).
    public static final int DEFAULT_DOCUMENT_LEAD_DAYS = 15;

    private static final Map<NotificationType, Definition> DEFINITIONS = new EnumMap<>(NotificationType.class);

    static {
        add(NotificationType.BLOCKING_DEFECT_REPORTED, "Defecto bloqueante reportado",
                "Un chofer reporta un defecto que deja al vehículo sin poder salir.", Audience.CONFIGURABLE, null, Lead.NONE);
        add(NotificationType.RECURRING_DEFECT, "Defecto recurrente",
                "El mismo control del checklist falla varias veces en el mismo vehículo.", Audience.CONFIGURABLE, null, Lead.NONE);
        add(NotificationType.WORK_ORDER_FINALIZED, "Orden de trabajo finalizada",
                "Se finaliza una orden de trabajo, interna o de un taller externo.", Audience.CONFIGURABLE, null, Lead.NONE);
        add(NotificationType.MAINTENANCE_DUE_SOON, "Mantenimiento por vencer",
                "Un plan asignado a un vehículo entra en la anticipación configurada.", Audience.CONFIGURABLE, null, Lead.DAYS_AND_KM);
        add(NotificationType.MAINTENANCE_OVERDUE, "Mantenimiento vencido",
                "Un plan asignado a un vehículo se pasa de su fecha o de su kilometraje.", Audience.CONFIGURABLE, null, Lead.NONE);
        add(NotificationType.DOCUMENT_DUE_SOON, "Documento por vencer",
                "Un documento de un vehículo (VTV, seguro…) entra en la anticipación configurada, y el día que vence.",
                Audience.CONFIGURABLE, null, Lead.DAYS);
        add(NotificationType.DOCUMENT_EXPIRED, "Documento vencido",
                "Vence un documento de un vehículo y el vehículo queda no disponible.", Audience.CONFIGURABLE, null, Lead.NONE);
        add(NotificationType.WORK_ORDER_ASSIGNED, "Orden de trabajo asignada",
                "A un técnico le asignan una orden de trabajo.", Audience.OWNER, "El técnico a cargo de la orden", Lead.NONE);
        add(NotificationType.WORK_ORDER_UNASSIGNED, "Orden de trabajo desasignada",
                "A un técnico le sacan una orden de trabajo que tenía.", Audience.OWNER, "El técnico que la tenía", Lead.NONE);
        add(NotificationType.WORK_ORDER_CANCELLED, "Orden de trabajo cancelada",
                "Se cancela una orden de trabajo que tenía un técnico.", Audience.OWNER, "El técnico que la tenía", Lead.NONE);
        add(NotificationType.TRIP_OPEN_REMINDER, "Recordatorio de viaje abierto",
                "El chofer no hizo el post-viaje y el viaje sigue abierto.", Audience.OWNER, "El chofer que abrió el viaje", Lead.NONE);
        add(NotificationType.VEHICLE_UNAVAILABLE, "Vehículo no disponible",
                "Vence un documento de un vehículo que un chofer tiene en viaje.", Audience.OWNER,
                "El chofer que tiene el vehículo en viaje", Lead.NONE);
        add(NotificationType.VEHICLE_AVAILABLE, "Vehículo disponible de nuevo",
                "El vehículo deja de estar no disponible: se resolvió el defecto bloqueante o se renovó el documento.",
                Audience.OWNER, "Los choferes que reportaron el defecto o a los que se avisó del bloqueo", Lead.NONE);
    }

    private NotificationCatalog() {
    }

    private static void add(NotificationType type, String label, String description, Audience audience,
                            String ownerDescription, Lead lead) {
        Integer leadDays = switch (lead) {
            case NONE -> null;
            case DAYS -> DEFAULT_DOCUMENT_LEAD_DAYS;
            case DAYS_AND_KM -> DEFAULT_LEAD_DAYS;
        };
        DEFINITIONS.put(type, new Definition(type, label, description, audience, ownerDescription, lead, leadDays));
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
        return List.of(NotificationType.BLOCKING_DEFECT_REPORTED, NotificationType.RECURRING_DEFECT,
                NotificationType.WORK_ORDER_FINALIZED,
                NotificationType.MAINTENANCE_DUE_SOON, NotificationType.MAINTENANCE_OVERDUE,
                NotificationType.DOCUMENT_DUE_SOON, NotificationType.DOCUMENT_EXPIRED,
                NotificationType.WORK_ORDER_ASSIGNED, NotificationType.WORK_ORDER_UNASSIGNED,
                NotificationType.WORK_ORDER_CANCELLED, NotificationType.TRIP_OPEN_REMINDER,
                NotificationType.VEHICLE_UNAVAILABLE, NotificationType.VEHICLE_AVAILABLE).indexOf(type);
    }
}
