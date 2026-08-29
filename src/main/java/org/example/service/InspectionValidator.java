package org.example.service;

import org.example.checklist.AccessoryKey;
import org.example.entity.CheckOutcome;
import org.example.entity.InspectionType;
import org.example.checklist.ChecklistCatalog;
import org.example.checklist.ChecklistItemDef;
import org.example.checklist.ChecklistItemType;
import org.example.entity.DefectSeverity;
import org.example.exception.InspectionValidationException;
import org.example.dto.ValidationErrorDetail;
import org.example.dto.ChecklistAnswerDto;
import org.example.dto.DefectDetailDto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves incoming answers against the server-side checklist catalog and
 * enforces CAM-11-dvir-contract.md section 4:
 *  - unknown itemIds (not in the catalog for this type/vehicle) are ignored;
 *  - every required catalog item (km, base items) must have an answer;
 *  - outcome=defect requires a defect detail; blocking additionally requires photoUrl.
 * Pure logic, no Spring/DB dependency, so it's cheap to unit test.
 */
public class InspectionValidator {

    public record ValidationOutcome(List<ChecklistAnswerDto> recognizedAnswers, Double odometerKm, boolean hasBlockingDefect) {
    }

    public ValidationOutcome validate(InspectionType type, List<AccessoryKey> accessories, List<ChecklistAnswerDto> answers) {
        List<ChecklistItemDef> catalogItems = type == InspectionType.PRE_TRIP
                ? ChecklistCatalog.preTripItems(accessories)
                : ChecklistCatalog.postTripItems();

        Map<String, ChecklistItemDef> catalogById = new LinkedHashMap<>();
        for (ChecklistItemDef item : catalogItems) {
            catalogById.put(item.id(), item);
        }

        Map<String, ChecklistAnswerDto> submittedById = new LinkedHashMap<>();
        for (ChecklistAnswerDto answer : answers) {
            if (answer != null && answer.itemId() != null && catalogById.containsKey(answer.itemId())) {
                submittedById.put(answer.itemId(), answer);
            }
            // itemIds outside the catalog are silently ignored -- the server
            // owns the checklist definition, it doesn't trust client metadata.
        }

        List<ValidationErrorDetail> details = new ArrayList<>();
        boolean hasBlockingDefect = false;
        Double odometerKm = null;
        String odometerItemId = type == InspectionType.PRE_TRIP
                ? ChecklistCatalog.ODOMETER_ITEM_ID_PRE_TRIP
                : ChecklistCatalog.ODOMETER_ITEM_ID_POST_TRIP;

        for (ChecklistItemDef item : catalogItems) {
            ChecklistAnswerDto answer = submittedById.get(item.id());

            if (answer == null) {
                if (item.required()) {
                    details.add(new ValidationErrorDetail(item.id(), "Este ítem es obligatorio."));
                }
                continue;
            }

            if (item.type() == ChecklistItemType.NUMBER) {
                if (answer.numberValue() == null) {
                    details.add(new ValidationErrorDetail(item.id(), "Debe indicar un valor numérico."));
                } else if (item.id().equals(odometerItemId)) {
                    odometerKm = answer.numberValue();
                }
                continue;
            }

            // CHECK item.
            CheckOutcome outcome = CheckOutcome.fromJson(answer.outcome());
            if (answer.outcome() == null || outcome == null) {
                details.add(new ValidationErrorDetail(item.id(), "Debe indicar un resultado ('ok' o 'defect')."));
                continue;
            }

            if (outcome == CheckOutcome.DEFECT) {
                DefectDetailDto defect = answer.defect();
                if (defect == null) {
                    details.add(new ValidationErrorDetail(item.id(), "Debe incluir el detalle del defecto."));
                    continue;
                }
                DefectSeverity severity = DefectSeverity.fromJson(defect.severity());
                if (severity == null) {
                    details.add(new ValidationErrorDetail(item.id(), "La severidad del defecto debe ser 'blocking' o 'non-blocking'."));
                    continue;
                }
                if (defect.description() == null || defect.description().isBlank()) {
                    details.add(new ValidationErrorDetail(item.id(), "La descripción del defecto es obligatoria."));
                    continue;
                }
                if (severity == DefectSeverity.BLOCKING && (defect.photoUrl() == null || defect.photoUrl().isBlank())) {
                    details.add(new ValidationErrorDetail(item.id(), "La foto es obligatoria para defectos bloqueantes."));
                    continue;
                }
                if (severity == DefectSeverity.BLOCKING) {
                    hasBlockingDefect = true;
                }
            }
        }

        if (!details.isEmpty()) {
            throw new InspectionValidationException(
                    "Faltan respuestas obligatorias o un defecto no cumple sus reglas.", details);
        }

        List<ChecklistAnswerDto> recognized = new ArrayList<>();
        for (ChecklistItemDef item : catalogItems) {
            ChecklistAnswerDto answer = submittedById.get(item.id());
            if (answer != null) {
                recognized.add(answer);
            }
        }

        return new ValidationOutcome(recognized, odometerKm, hasBlockingDefect);
    }
}
