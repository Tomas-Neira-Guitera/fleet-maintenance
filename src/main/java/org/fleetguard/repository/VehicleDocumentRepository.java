package org.fleetguard.repository;

import org.fleetguard.entity.VehicleDocument;
import org.fleetguard.entity.VehicleDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleDocumentRepository extends JpaRepository<VehicleDocument, UUID> {

    /** Vigentes y reemplazados de un vehículo; los dados de baja no se muestran. */
    List<VehicleDocument> findByVehicleIdAndActiveTrue(UUID vehicleId);

    Optional<VehicleDocument> findByIdAndVehicleIdAndActiveTrue(UUID id, UUID vehicleId);

    /** El vigente de un tipo (para "otro" puede haber varios: se usa solo con los demás tipos). */
    Optional<VehicleDocument> findFirstByVehicleIdAndTypeAndActiveTrueAndReplacedByIdIsNull(UUID vehicleId,
                                                                                         VehicleDocumentType type);

    /** Vigentes con vencimiento: alimentan los avisos diarios. */
    @Query("select d from VehicleDocument d where d.active = true and d.replacedById is null and d.expiresAt is not null")
    List<VehicleDocument> findCurrentWithExpiry();

    /** Vigentes vencidos antes de {@code today} de esos vehículos: el motivo de "No disponible". */
    @Query("select d from VehicleDocument d where d.active = true and d.replacedById is null "
            + "and d.expiresAt < :today and d.vehicleId in :vehicleIds")
    List<VehicleDocument> findExpiredCurrent(@Param("vehicleIds") Collection<UUID> vehicleIds, @Param("today") LocalDate today);
}
