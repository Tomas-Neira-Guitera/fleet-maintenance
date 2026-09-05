package org.example.mapper;

import org.example.entity.CheckOutcome;
import org.example.entity.Defect;
import org.example.entity.DefectSeverity;
import org.example.entity.Inspection;
import org.example.entity.InspectionAnswer;
import org.example.entity.InspectionType;
import org.example.entity.Trip;
import org.example.entity.Vehicle;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefectMapperTest {

    private final DefectMapper mapper = new DefectMapper();

    /** El id es @GeneratedValue; en el test se setea por reflexión, sin persistencia real (mismo patrón que DefectServiceTest). */
    private static void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    @Test
    void toDtoIncluyeElNombreDeQuienReporto() throws Exception {
        Vehicle vehicle = new Vehicle("AB123CD", "Mercedes-Benz", "Sprinter");
        UUID vehicleId = UUID.randomUUID();
        setId(vehicle, vehicleId);
        Trip trip = new Trip(vehicle, Instant.now());
        Inspection inspection = new Inspection(trip, vehicleId, "driver-1", "Marcos", InspectionType.PRE_TRIP,
                Instant.now(), 1000.0, null, true);
        InspectionAnswer answer = new InspectionAnswer("ext-luces", CheckOutcome.DEFECT, null);
        answer.setInspection(inspection);

        Defect defect = new Defect(DefectSeverity.BLOCKING, "Pérdida de aceite en motor", null, Instant.now());
        setId(defect, UUID.randomUUID());
        answer.attachDefect(defect);

        var dto = mapper.toDto(defect, "AB123CD");

        assertEquals("Marcos", dto.reportedBy());
    }
}
