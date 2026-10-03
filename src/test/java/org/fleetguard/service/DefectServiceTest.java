package org.fleetguard.service;

import org.fleetguard.dto.DefectDto;
import org.fleetguard.entity.CheckOutcome;
import org.fleetguard.entity.Defect;
import org.fleetguard.entity.DefectSeverity;
import org.fleetguard.entity.Inspection;
import org.fleetguard.entity.InspectionAnswer;
import org.fleetguard.entity.InspectionType;
import org.fleetguard.entity.Trip;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.mapper.DefectMapper;
import org.fleetguard.repository.DefectRepository;
import org.fleetguard.repository.VehicleRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class DefectServiceTest {

    private final DefectRepository defectRepository = mock(DefectRepository.class);
    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final DefectMapper defectMapper = mock(DefectMapper.class);
    private final DefectService service = new DefectService(defectRepository, vehicleRepository, defectMapper);

    /** Los ids de las entidades son @GeneratedValue; en el test se setean por reflexión, sin persistencia real. */
    private static void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    private Defect defectWith(DefectSeverity severity, Instant createdAt, UUID vehicleId, String label) throws Exception {
        Vehicle vehicle = new Vehicle("AB123CD", "Mercedes-Benz", "Sprinter");
        setId(vehicle, vehicleId);

        Trip trip = new Trip(vehicle, Instant.now());
        Inspection inspection = new Inspection(trip, vehicleId, "driver-1", "Marcos", InspectionType.PRE_TRIP,
                Instant.now(), 1000.0, null, true);
        InspectionAnswer answer = new InspectionAnswer("ext-luces", CheckOutcome.DEFECT, null);
        answer.setInspection(inspection);

        Defect defect = new Defect(severity, label, null, createdAt);
        answer.attachDefect(defect);
        return defect;
    }

    @Test
    void ordenaPorSeveridadYLuegoPorFechaMasRecientePrimero() throws Exception {
        UUID vehicleId = UUID.randomUUID();
        Instant now = Instant.now();

        Defect nonBlockingReciente = defectWith(DefectSeverity.NON_BLOCKING, now, vehicleId, "no bloqueante reciente");
        Defect blockingViejo = defectWith(DefectSeverity.BLOCKING, now.minusSeconds(60), vehicleId, "bloqueante viejo");
        Defect blockingReciente = defectWith(DefectSeverity.BLOCKING, now, vehicleId, "bloqueante reciente");

        when(defectRepository.findAllWithInspection())
                .thenReturn(List.of(nonBlockingReciente, blockingViejo, blockingReciente));
        when(vehicleRepository.findAllById(any())).thenReturn(List.of());
        when(defectMapper.toDto(any(), any(), any())).thenAnswer(invocation -> {
            Defect d = invocation.getArgument(0);
            return new DefectDto(d.getId() == null ? d.getDescription() : d.getId().toString(),
                    d.getSeverity().toJson(), d.getDescription(), null, d.getCreatedAt().toString(),
                    d.getInspectionAnswer().getInspection().getVehicleId().toString(), null, d.getStatus(),
                    d.getInspectionAnswer().getInspection().getDriverName());
        });

        List<DefectDto> result = service.listDefects();

        assertEquals(3, result.size());
        assertEquals("bloqueante reciente", result.get(0).description());
        assertEquals("bloqueante viejo", result.get(1).description());
        assertEquals("no bloqueante reciente", result.get(2).description());
    }
}
