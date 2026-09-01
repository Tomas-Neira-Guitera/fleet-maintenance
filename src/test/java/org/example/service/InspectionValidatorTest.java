package org.example.service;

import org.example.entity.InspectionType;
import org.example.exception.InspectionValidationException;
import org.example.dto.ChecklistAnswerDto;
import org.example.dto.DefectDetailDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InspectionValidatorTest {

    private final InspectionValidator validator = new InspectionValidator();

    private List<ChecklistAnswerDto> minimalValidPreTripAnswers() {
        return List.of(
                new ChecklistAnswerDto("ext-luces", "ok", null, null),
                new ChecklistAnswerDto("ext-neumaticos", "ok", null, null),
                new ChecklistAnswerDto("ext-carroceria", "ok", null, null),
                new ChecklistAnswerDto("ext-fugas", "ok", null, null),
                new ChecklistAnswerDto("int-km", null, 12345.0, null),
                new ChecklistAnswerDto("int-documentacion", "ok", null, null),
                new ChecklistAnswerDto("int-testigos", "ok", null, null)
        );
    }

    @Test
    void acceptsAMinimalValidPreTripSubmission() {
        InspectionValidator.ValidationOutcome outcome =
                validator.validate(InspectionType.PRE_TRIP, minimalValidPreTripAnswers());

        assertEquals(12345.0, outcome.odometerKm());
        assertFalse(outcome.hasBlockingDefect());
        assertEquals(7, outcome.recognizedAnswers().size());
    }

    @Test
    void rejectsAPreTripSubmissionMissingTheOdometerReading() {
        List<ChecklistAnswerDto> answers = minimalValidPreTripAnswers().stream()
                .filter(a -> !a.itemId().equals("int-km"))
                .toList();

        InspectionValidationException ex = assertThrows(InspectionValidationException.class,
                () -> validator.validate(InspectionType.PRE_TRIP, answers));

        assertTrue(ex.getDetails().stream().anyMatch(d -> d.itemId().equals("int-km")));
    }

    @Test
    void ignoresUnknownItemIdsSentByTheClient() {
        List<ChecklistAnswerDto> answers = new java.util.ArrayList<>(minimalValidPreTripAnswers());
        answers.add(new ChecklistAnswerDto("some-made-up-item", "ok", null, null));

        InspectionValidator.ValidationOutcome outcome =
                validator.validate(InspectionType.PRE_TRIP, answers);

        assertEquals(7, outcome.recognizedAnswers().size());
        assertTrue(outcome.recognizedAnswers().stream().noneMatch(a -> a.itemId().equals("some-made-up-item")));
    }

    @Test
    void nonBlockingDefectRequiresOnlyADescription() {
        List<ChecklistAnswerDto> answers = new java.util.ArrayList<>(minimalValidPreTripAnswers().stream()
                .filter(a -> !a.itemId().equals("ext-luces")).toList());
        answers.add(new ChecklistAnswerDto("ext-luces", "defect", null,
                new DefectDetailDto("non-blocking", "Foco trasero tenue", null)));

        InspectionValidator.ValidationOutcome outcome =
                validator.validate(InspectionType.PRE_TRIP, answers);

        assertFalse(outcome.hasBlockingDefect());
    }

    @Test
    void nonBlockingDefectWithoutDescriptionIsRejected() {
        List<ChecklistAnswerDto> answers = new java.util.ArrayList<>(minimalValidPreTripAnswers().stream()
                .filter(a -> !a.itemId().equals("ext-luces")).toList());
        answers.add(new ChecklistAnswerDto("ext-luces", "defect", null,
                new DefectDetailDto("non-blocking", "", null)));

        InspectionValidationException ex = assertThrows(InspectionValidationException.class,
                () -> validator.validate(InspectionType.PRE_TRIP, answers));

        assertTrue(ex.getDetails().stream().anyMatch(d -> d.itemId().equals("ext-luces")));
    }

    @Test
    void blockingDefectRequiresDescriptionAndPhoto() {
        List<ChecklistAnswerDto> answers = new java.util.ArrayList<>(minimalValidPreTripAnswers().stream()
                .filter(a -> !a.itemId().equals("ext-neumaticos")).toList());
        answers.add(new ChecklistAnswerDto("ext-neumaticos", "defect", null,
                new DefectDetailDto("blocking", "Neumático pinchado", null)));

        InspectionValidationException ex = assertThrows(InspectionValidationException.class,
                () -> validator.validate(InspectionType.PRE_TRIP, answers));

        assertTrue(ex.getDetails().stream().anyMatch(d -> d.itemId().equals("ext-neumaticos")));
    }

    @Test
    void blockingDefectWithDescriptionAndPhotoIsAccepted() {
        List<ChecklistAnswerDto> answers = new java.util.ArrayList<>(minimalValidPreTripAnswers().stream()
                .filter(a -> !a.itemId().equals("ext-neumaticos")).toList());
        answers.add(new ChecklistAnswerDto("ext-neumaticos", "defect", null,
                new DefectDetailDto("blocking", "Neumático pinchado", "http://localhost:8080/api/photos/abc")));

        InspectionValidator.ValidationOutcome outcome =
                validator.validate(InspectionType.PRE_TRIP, answers);

        assertTrue(outcome.hasBlockingDefect());
    }

    @Test
    void postTripUsesItsOwnCatalogAndOdometerItem() {
        List<ChecklistAnswerDto> answers = List.of(
                new ChecklistAnswerDto("post-danos", "ok", null, null),
                new ChecklistAnswerDto("post-luces", "ok", null, null),
                new ChecklistAnswerDto("post-fugas", "ok", null, null),
                new ChecklistAnswerDto("post-km", null, 12400.0, null),
                // Un itemId de pre-trip enviado por error debe ignorarse, no romper.
                new ChecklistAnswerDto("int-km", null, 1.0, null)
        );

        InspectionValidator.ValidationOutcome outcome =
                validator.validate(InspectionType.POST_TRIP, answers);

        assertEquals(12400.0, outcome.odometerKm());
        assertEquals(4, outcome.recognizedAnswers().size());
    }
}
