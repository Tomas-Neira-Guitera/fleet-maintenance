package org.fleetguard.service;

import org.fleetguard.dto.OccurredOnTripDto;
import org.fleetguard.entity.CheckOutcome;
import org.fleetguard.entity.Defect;
import org.fleetguard.entity.DefectSeverity;
import org.fleetguard.entity.Inspection;
import org.fleetguard.entity.InspectionAnswer;
import org.fleetguard.entity.InspectionType;
import org.fleetguard.entity.Trip;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.repository.InspectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CAM-146 -- qué defectos del post-viaje aparecieron durante el viaje. */
class TripDamageServiceTest {

    private static final Instant STARTED = Instant.parse("2026-11-02T11:00:00Z");
    private static final Instant ENDED = Instant.parse("2026-11-02T20:00:00Z");

    private final InspectionRepository inspectionRepository = mock(InspectionRepository.class);
    private final TripDamageService service = new TripDamageService(inspectionRepository);

    private Vehicle vehicle;
    private Trip trip;

    private static void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    @BeforeEach
    void setUp() throws Exception {
        vehicle = new Vehicle("AB123CD", "Ford", "Cargo");
        setId(vehicle, UUID.randomUUID());
        trip = new Trip(vehicle, STARTED, UUID.randomUUID(), "carlos.gomez");
        setId(trip, UUID.randomUUID());
        trip.close(ENDED, UUID.randomUUID(), "carlos.gomez");
    }

    /** Un pre-viaje del viaje con esas respuestas: itemId -> OK o con defecto. */
    private Inspection preTrip(Map<String, CheckOutcome> outcomes) throws Exception {
        Inspection inspection = new Inspection(trip, vehicle.getId(), "driver-1", "carlos.gomez",
                InspectionType.PRE_TRIP, STARTED, 1000.0, null, false);
        setId(inspection, UUID.randomUUID());
        outcomes.forEach((itemId, outcome) -> inspection.addAnswer(new InspectionAnswer(itemId, outcome, null)));
        return inspection;
    }

    private Defect defect(InspectionType type, String itemId) throws Exception {
        Instant at = type == InspectionType.PRE_TRIP ? STARTED : ENDED;
        Inspection inspection = new Inspection(trip, vehicle.getId(), "driver-1", "carlos.gomez", type, at, 1200.0,
                null, false);
        InspectionAnswer answer = new InspectionAnswer(itemId, CheckOutcome.DEFECT, null);
        inspection.addAnswer(answer);
        Defect defect = new Defect(DefectSeverity.NON_BLOCKING, "Falla", null, at);
        setId(defect, UUID.randomUUID());
        answer.attachDefect(defect);
        return defect;
    }

    @Test
    void lucesOkEnElPreViajeYConFallaEnElPostViajeSeMarcaConElViajeYElPreViaje() throws Exception {
        Inspection preTrip = preTrip(Map.of("ext-luces", CheckOutcome.OK));
        when(inspectionRepository.findPreTripsWithAnswersByTripIds(any())).thenReturn(List.of(preTrip));
        Defect lights = defect(InspectionType.POST_TRIP, "post-luces");

        OccurredOnTripDto result = service.occurredOnTrip(List.of(lights)).get(lights.getId());

        assertNotNull(result);
        assertEquals(trip.getId().toString(), result.tripId());
        assertEquals(STARTED.toString(), result.tripStartedAt());
        assertEquals(ENDED.toString(), result.tripEndedAt());
        assertEquals(preTrip.getId().toString(), result.preTripInspectionId());
        assertEquals(STARTED.toString(), result.preTripAt());
    }

    @Test
    void siYaFallabaEnElPreViajeNoSeMarca() throws Exception {
        when(inspectionRepository.findPreTripsWithAnswersByTripIds(any()))
                .thenReturn(List.of(preTrip(Map.of("ext-luces", CheckOutcome.DEFECT))));
        Defect lights = defect(InspectionType.POST_TRIP, "post-luces");

        assertFalse(service.occurredOnTrip(List.of(lights)).containsKey(lights.getId()));
    }

    @Test
    void lasFugasTambienSeComparanConElPreViaje() throws Exception {
        when(inspectionRepository.findPreTripsWithAnswersByTripIds(any()))
                .thenReturn(List.of(preTrip(Map.of("ext-fugas", CheckOutcome.OK, "ext-luces", CheckOutcome.DEFECT))));
        Defect leaks = defect(InspectionType.POST_TRIP, "post-fugas");

        assertTrue(service.occurredOnTrip(List.of(leaks)).containsKey(leaks.getId()));
    }

    @Test
    void losDanosNuevosEnLaCarroceriaSiempreSeMarcan() throws Exception {
        when(inspectionRepository.findPreTripsWithAnswersByTripIds(any()))
                .thenReturn(List.of(preTrip(Map.of("ext-carroceria", CheckOutcome.DEFECT))));
        Defect damage = defect(InspectionType.POST_TRIP, "post-danos");

        assertTrue(service.occurredOnTrip(List.of(damage)).containsKey(damage.getId()));
    }

    @Test
    void unDefectoDelPreViajeNuncaSeMarcaYNoHaceFaltaConsultar() throws Exception {
        Defect lights = defect(InspectionType.PRE_TRIP, "ext-luces");

        assertTrue(service.occurredOnTrip(List.of(lights)).isEmpty());
        verify(inspectionRepository, never()).findPreTripsWithAnswersByTripIds(any());
    }

    @Test
    void unControlSinEquivalenteEnElPreViajeNoSeMarca() throws Exception {
        when(inspectionRepository.findPreTripsWithAnswersByTripIds(any()))
                .thenReturn(List.of(preTrip(Map.of("ext-luces", CheckOutcome.OK))));
        Defect other = defect(InspectionType.POST_TRIP, "post-otro");

        assertFalse(service.occurredOnTrip(List.of(other)).containsKey(other.getId()));
    }

    @Test
    void sinPreViajeGuardadoSoloSeMarcanLosDanosNuevos() throws Exception {
        when(inspectionRepository.findPreTripsWithAnswersByTripIds(any())).thenReturn(List.of());
        Defect lights = defect(InspectionType.POST_TRIP, "post-luces");
        Defect damage = defect(InspectionType.POST_TRIP, "post-danos");

        Map<UUID, OccurredOnTripDto> result = service.occurredOnTrip(List.of(lights, damage));

        assertFalse(result.containsKey(lights.getId()));
        assertEquals(trip.getId().toString(), result.get(damage.getId()).tripId());
    }

    @Test
    void todoElListadoSeResuelveConUnaSolaConsulta() throws Exception {
        when(inspectionRepository.findPreTripsWithAnswersByTripIds(any()))
                .thenReturn(List.of(preTrip(Map.of("ext-luces", CheckOutcome.OK, "ext-fugas", CheckOutcome.OK))));

        service.occurredOnTrip(List.of(defect(InspectionType.POST_TRIP, "post-luces"),
                defect(InspectionType.POST_TRIP, "post-fugas"), defect(InspectionType.POST_TRIP, "post-danos")));

        verify(inspectionRepository, times(1)).findPreTripsWithAnswersByTripIds(any());
    }
}
