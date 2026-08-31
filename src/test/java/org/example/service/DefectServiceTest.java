package org.example.service;

import org.example.dto.DefectDto;
import org.example.entity.CheckOutcome;
import org.example.entity.Defect;
import org.example.entity.DefectSeverity;
import org.example.entity.Inspection;
import org.example.entity.InspectionAnswer;
import org.example.entity.InspectionType;
import org.example.entity.Trip;
import org.example.entity.Vehicle;
import org.example.mapper.DefectMapper;
import org.example.repository.DefectRepository;
import org.example.repository.VehicleRepository;
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
        when(defectMapper.toDto(any(), any())).thenAnswer(invocation -> {
            Defect d = invocation.getArgument(0);
            return new DefectDto(d.getId() == null ? d.getDescription() : d.getId().toString(),
                    d.getSeverity().toJson(), d.getDescription(), null, d.getCreatedAt().toString(), null, d.getStatus());
        });

        List<DefectDto> result = service.listDefects();

        assertEquals(3, result.size());
        assertEquals("bloqueante reciente", result.get(0).description());
        assertEquals("bloqueante viejo", result.get(1).description());
        assertEquals("no bloqueante reciente", result.get(2).description());
    }
}
