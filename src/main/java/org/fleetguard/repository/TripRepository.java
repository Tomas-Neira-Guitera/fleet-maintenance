package org.fleetguard.repository;

import org.fleetguard.entity.Trip;
import org.fleetguard.entity.TripStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {

    Optional<Trip> findFirstByVehicle_IdAndStatus(UUID vehicleId, TripStatus status);

    /** Todos los viajes abiertos, con su vehículo: arma el listado de flota sin una consulta por vehículo. */
    @Query("select t from Trip t join fetch t.vehicle where t.status = :status")
    List<Trip> findByStatusWithVehicle(@Param("status") TripStatus status);

    /**
     * Viajes abiertos antes de {@code startedBefore} a los que todavía no se les mandó el recordatorio
     * de cerrarlos (CAM-101), con su vehículo. Sin quién lo abrió no hay a quién avisarle.
     */
    @Query("select t from Trip t join fetch t.vehicle where t.status = org.fleetguard.entity.TripStatus.OPEN "
            + "and t.openedById is not null and t.reminderSentAt is null and t.startedAt < :startedBefore")
    List<Trip> findOpenTripsToRemind(@Param("startedBefore") Instant startedBefore);

    /**
     * Marca que se manda el recordatorio (CAM-101), solo si el viaje sigue abierto y sin recordatorio.
     * Devuelve 1 si lo marcó y 0 si no: el chofer lo cerró desde que se leyó, u otra corrida ya lo
     * marcó. Es un update de esa sola columna y no un save del viaje leído antes, que lo volvería a abrir.
     */
    @Modifying
    @Query("update Trip t set t.reminderSentAt = :sentAt where t.id = :id "
            + "and t.status = org.fleetguard.entity.TripStatus.OPEN and t.reminderSentAt is null")
    int markReminderSent(@Param("id") UUID id, @Param("sentAt") Instant sentAt);

    /**
     * Viajes iniciados en el período, por chofer que los abrió (CAM-91). No cuenta los que se
     * cerraron en el mismo instante: son pre-viajes con defecto bloqueante (CAM-86), no viajes.
     */
    @Query("select new org.fleetguard.repository.UserCount(t.openedById, count(t)) from Trip t "
            + "where t.openedById is not null and t.startedAt >= :from and t.startedAt < :to "
            + "and (t.endedAt is null or t.endedAt <> t.startedAt) group by t.openedById")
    List<UserCount> countTripsByDriver(@Param("from") Instant from, @Param("to") Instant to);
}
