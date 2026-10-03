package org.fleetguard.mapper;

import org.fleetguard.entity.CheckOutcome;
import org.fleetguard.entity.Defect;
import org.fleetguard.entity.DefectSeverity;
import org.fleetguard.entity.Inspection;
import org.fleetguard.entity.InspectionAnswer;
import org.fleetguard.entity.InspectionType;
import org.fleetguard.entity.Trip;
import org.fleetguard.entity.Vehicle;
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

        var dto = mapper.toDto(defect, vehicleId.toString(), "AB123CD");

        assertEquals("Marcos", dto.reportedBy());
    }
}
