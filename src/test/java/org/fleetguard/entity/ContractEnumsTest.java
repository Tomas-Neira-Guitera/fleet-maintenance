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
        assertContract(InspectionType.class, InspectionType::toJson, InspectionType::fromJson,
                Map.of(InspectionType.PRE_TRIP, "pre-trip", InspectionType.POST_TRIP, "post-trip"));
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
                        WorkOrderStatus.FINALIZADA, "finalizada", WorkOrderStatus.CANCELADA, "cancelada"));
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
