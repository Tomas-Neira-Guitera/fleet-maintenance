package org.fleetguard.entity;

import org.fleetguard.entity.checklist.ChecklistItemType;
import org.fleetguard.entity.checklist.ChecklistSection;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Los enums del modelo se guardan en la base con su nombre en Java, pero viajan en el JSON con
 * el texto del contrato (openapi.yaml), que es el que compara el frontend ("en_proceso",
 * "non-blocking"...). Si alguien renombra una constante o cambia un texto, el frontend se rompe
 * sin que falle nada al compilar: estos tests fijan cada valor contra el contrato.
 */
class ContractEnumsTest {

    /**
     * Comprueba que el enum completo se traduce exactamente a los textos esperados (ni uno más,
     * ni uno menos), que cada texto vuelve al mismo valor y que un texto desconocido es null.
     */
    private static <E extends Enum<E>> void assertContract(Class<E> type, Function<E, String> toJson,
                                                           Function<String, E> fromJson, Map<E, String> expected) {
        Map<E, String> actual = new LinkedHashMap<>();
        Arrays.stream(type.getEnumConstants()).forEach(value -> actual.put(value, toJson.apply(value)));
        assertEquals(expected, actual, type.getSimpleName() + ": los textos no coinciden con openapi.yaml");
        expected.forEach((value, json) -> assertEquals(value, fromJson.apply(json), type.getSimpleName() + " <- " + json));
        assertNull(fromJson.apply("valor-que-no-existe"), type.getSimpleName() + ": un texto desconocido tiene que ser null");
        assertNull(fromJson.apply(null), type.getSimpleName() + ": null tiene que ser null, no una excepción");
    }

    @Test
    void inspeccionesYDefectos() {
        // CAM-139: on-road solo sale (source de un defecto); fromJson lee el type de POST /inspections,
        // que no lo acepta. Por eso no pasa por assertContract, que exige ida y vuelta.
        assertEquals(Map.of(InspectionType.PRE_TRIP, "pre-trip", InspectionType.POST_TRIP, "post-trip",
                        InspectionType.ON_ROAD, "on-road"),
                Arrays.stream(InspectionType.values()).collect(java.util.stream.Collectors.toMap(v -> v, InspectionType::toJson)));
        assertEquals(InspectionType.PRE_TRIP, InspectionType.fromJson("pre-trip"));
        assertEquals(InspectionType.POST_TRIP, InspectionType.fromJson("post-trip"));
        assertNull(InspectionType.fromJson("on-road"));
        assertNull(InspectionType.fromJson(null));
        assertContract(CheckOutcome.class, CheckOutcome::toJson, CheckOutcome::fromJson,
                Map.of(CheckOutcome.OK, "ok", CheckOutcome.DEFECT, "defect"));
        assertContract(DefectSeverity.class, DefectSeverity::toJson, DefectSeverity::fromJson,
                Map.of(DefectSeverity.NON_BLOCKING, "non-blocking", DefectSeverity.BLOCKING, "blocking"));
    }

    @Test
    void checklist() {
        assertContract(ChecklistItemType.class, ChecklistItemType::toJson, ChecklistItemType::fromJson,
                Map.of(ChecklistItemType.CHECK, "check", ChecklistItemType.NUMBER, "number"));
        assertContract(ChecklistSection.class, ChecklistSection::toJson, ChecklistSection::fromJson,
                Map.of(ChecklistSection.EXTERIOR, "exterior", ChecklistSection.INTERIOR, "interior",
                        ChecklistSection.POSTTRIP, "posttrip"));
    }

    @Test
    void mantenimientoPreventivo() {
        assertContract(IntervalType.class, IntervalType::toJson, IntervalType::fromJson,
                Map.of(IntervalType.KM, "km", IntervalType.TIME, "time", IntervalType.BOTH, "both"));
        assertContract(ScheduleSourceType.class, ScheduleSourceType::toJson, ScheduleSourceType::fromJson,
                Map.of(ScheduleSourceType.ASSIGNMENT, "assignment", ScheduleSourceType.DEFECT, "defect",
                        ScheduleSourceType.MANUAL, "manual"));
        assertContract(ScheduleStatus.class, ScheduleStatus::toJson, ScheduleStatus::fromJson,
                Map.of(ScheduleStatus.SCHEDULED, "scheduled", ScheduleStatus.DONE, "done",
                        ScheduleStatus.CANCELLED, "cancelled"));
    }

    @Test
    void ordenesDeTrabajo() {
        assertContract(WorkOrderStatus.class, WorkOrderStatus::toJson, WorkOrderStatus::fromJson,
                Map.of(WorkOrderStatus.ASIGNADA, "asignada", WorkOrderStatus.EN_PROCESO, "en_proceso",
                        WorkOrderStatus.EN_ESPERA, "en_espera",
                        WorkOrderStatus.FINALIZADA, "finalizada", WorkOrderStatus.CANCELADA, "cancelada"));
        assertContract(WorkOrderPauseReason.class, WorkOrderPauseReason::toJson, WorkOrderPauseReason::fromJson,
                Map.of(WorkOrderPauseReason.ESPERANDO_REPUESTO, "esperando_repuesto",
                        WorkOrderPauseReason.ESPERANDO_PROVEEDOR, "esperando_proveedor",
                        WorkOrderPauseReason.ESPERANDO_VEHICULO, "esperando_vehiculo",
                        WorkOrderPauseReason.OTRO, "otro"));
        assertContract(WorkOrderSourceType.class, WorkOrderSourceType::toJson, WorkOrderSourceType::fromJson,
                Map.of(WorkOrderSourceType.SCHEDULED_MAINTENANCE, "scheduled_maintenance",
                        WorkOrderSourceType.DEFECT, "defect", WorkOrderSourceType.MANUAL, "manual"));
        assertContract(WorkOrderExecutionType.class, WorkOrderExecutionType::toJson, WorkOrderExecutionType::fromJson,
                Map.of(WorkOrderExecutionType.INTERNO, "interno", WorkOrderExecutionType.EXTERNO, "externo"));
        assertContract(WorkOrderExpenseCategory.class, WorkOrderExpenseCategory::toJson, WorkOrderExpenseCategory::fromJson,
                Map.of(WorkOrderExpenseCategory.REPUESTO, "repuesto", WorkOrderExpenseCategory.MANO_DE_OBRA, "mano_de_obra",
                        WorkOrderExpenseCategory.OTRO, "otro"));
    }

    @Test
    void avisos() {
        assertContract(NotificationType.class, NotificationType::toJson, NotificationType::fromJson,
                Map.ofEntries(Map.entry(NotificationType.WORK_ORDER_ASSIGNED, "work_order_assigned"),
                        Map.entry(NotificationType.WORK_ORDER_UNASSIGNED, "work_order_unassigned"),
                        Map.entry(NotificationType.WORK_ORDER_CANCELLED, "work_order_cancelled"),
                        Map.entry(NotificationType.TRIP_OPEN_REMINDER, "trip_open_reminder"),
                        Map.entry(NotificationType.BLOCKING_DEFECT_REPORTED, "blocking_defect_reported"),
                        Map.entry(NotificationType.WORK_ORDER_FINALIZED, "work_order_finalized"),
                        Map.entry(NotificationType.MAINTENANCE_DUE_SOON, "maintenance_due_soon"),
                        Map.entry(NotificationType.MAINTENANCE_OVERDUE, "maintenance_overdue"),
                        Map.entry(NotificationType.RECURRING_DEFECT, "recurring_defect"),
                        Map.entry(NotificationType.DOCUMENT_DUE_SOON, "document_due_soon"),
                        Map.entry(NotificationType.DOCUMENT_EXPIRED, "document_expired"),
                        Map.entry(NotificationType.VEHICLE_UNAVAILABLE, "vehicle_unavailable"),
                        Map.entry(NotificationType.VEHICLE_AVAILABLE, "vehicle_available"),
                        Map.entry(NotificationType.TRIP_KM_ANOMALY, "trip_km_anomaly")));
    }

    @Test
    void motivosDeKmFueraDeLoNormal() {
        Map<TripKmAlertReason, String> expected = Map.of(TripKmAlertReason.ABOVE_AVERAGE, "above_average",
                TripKmAlertReason.BELOW_AVERAGE, "below_average",
                TripKmAlertReason.BELOW_VEHICLE_KM, "below_vehicle_km");
        for (TripKmAlertReason reason : TripKmAlertReason.values()) {
            assertEquals(expected.get(reason), reason.toJson());
        }
    }

    @Test
    void canalesDeAviso() {
        assertContract(NotificationChannel.class, NotificationChannel::toJson, NotificationChannel::fromJson,
                Map.of(NotificationChannel.IN_APP, "in_app", NotificationChannel.PUSH, "push",
                        NotificationChannel.EMAIL, "email"));
    }

    @Test
    void elEstadoDeMantenimientoSeOrdenaPorGravedad() {
        assertEquals(Map.of(MaintenanceStatus.AL_DIA, "al_dia", MaintenanceStatus.POR_VENCER, "por_vencer",
                        MaintenanceStatus.VENCIDO, "vencido"),
                Map.of(MaintenanceStatus.AL_DIA, MaintenanceStatus.AL_DIA.toJson(),
                        MaintenanceStatus.POR_VENCER, MaintenanceStatus.POR_VENCER.toJson(),
                        MaintenanceStatus.VENCIDO, MaintenanceStatus.VENCIDO.toJson()));
        // La tabla de flota y el "peor estado" de un vehículo dependen de este orden.
        assertEquals(0, MaintenanceStatus.AL_DIA.severity());
        assertEquals(1, MaintenanceStatus.POR_VENCER.severity());
        assertEquals(2, MaintenanceStatus.VENCIDO.severity());
    }
}
