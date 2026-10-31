package org.fleetguard.repository;

import org.fleetguard.entity.Defect;
import org.fleetguard.entity.DefectSeverity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DefectRepository extends JpaRepository<Defect, UUID> {

    /** Trae inspectionAnswer + inspection en la misma consulta para no hacer N+1 al armar el listado. */
    @Query("select d from Defect d join fetch d.inspectionAnswer a join fetch a.inspection i")
    List<Defect> findAllWithInspection();

    /**
     * Igual que {@link #findAllWithInspection()}, filtrado por vehículo, más reciente primero --
     * alimenta el historial de vehículo (CAM-22), el historial combinado de CAM-15 y el selector
     * de CAM-14.
     */
    @Query("select d from Defect d join fetch d.inspectionAnswer a join fetch a.inspection i "
            + "where i.vehicleId = :vehicleId order by d.createdAt desc")
    List<Defect> findByVehicleIdWithInspection(@Param("vehicleId") UUID vehicleId);

    /** Un defecto con su inspección ya cargada (hace falta para reportedBy) -- lo usa el detalle de OT (CAM-60). */
    @Query("select d from Defect d join fetch d.inspectionAnswer a join fetch a.inspection i where d.id = :id")
    Optional<Defect> findByIdWithInspection(@Param("id") UUID id);

    /** CAM-89: un defecto bloqueante sin resolver impide que el vehículo salga. */
    @Query("select count(d) > 0 from Defect d join d.inspectionAnswer a join a.inspection i "
            + "where i.vehicleId = :vehicleId and d.status = 'open' "
            + "and d.severity = org.fleetguard.entity.DefectSeverity.BLOCKING")
    boolean existsOpenBlockingByVehicleId(@Param("vehicleId") UUID vehicleId);

    /** Ids de todos los vehículos bloqueados, para marcar el listado de flota en una sola consulta. */
    @Query("select distinct i.vehicleId from Defect d join d.inspectionAnswer a join a.inspection i "
            + "where d.status = 'open' and d.severity = org.fleetguard.entity.DefectSeverity.BLOCKING")
    List<UUID> findVehicleIdsWithOpenBlockingDefect();

    /**
     * Bloqueantes reportados antes de {@code before} que pueden tocar un período que empieza en {@code after}:
     * abiertos, resueltos después de {@code after}, o resueltos antes de CAM-134 (sin fecha, la decide el servicio).
     * Con su inspección, de donde sale el vehículo -- tiempo fuera de servicio, CAM-134.
     */
    @Query("select d from Defect d join fetch d.inspectionAnswer a join fetch a.inspection i "
            + "where d.severity = org.fleetguard.entity.DefectSeverity.BLOCKING and d.createdAt < :before "
            + "and (d.status = 'open' or d.resolvedAt is null or d.resolvedAt > :after)")
    List<Defect> findBlockingInPeriodWithInspection(@Param("after") Instant after, @Param("before") Instant before);

    /** Defectos reportados desde {@code from}, con su respuesta e inspección -- CAM-135, todos los vehículos. */
    @Query("select d from Defect d join fetch d.inspectionAnswer a join fetch a.inspection i "
            + "where d.createdAt >= :from")
    List<Defect> findReportedSinceWithInspection(@Param("from") Instant from);

    /** Igual que {@link #findReportedSinceWithInspection}, de un solo vehículo -- CAM-135. */
    @Query("select d from Defect d join fetch d.inspectionAnswer a join fetch a.inspection i "
            + "where i.vehicleId = :vehicleId and d.createdAt >= :from")
    List<Defect> findReportedSinceByVehicleIdWithInspection(@Param("vehicleId") UUID vehicleId,
                                                            @Param("from") Instant from);

    /** CAM-127: los bloqueantes abiertos de esos vehículos, con su inspección (el motivo de "No disponible"). */
    @Query("select d from Defect d join fetch d.inspectionAnswer a join fetch a.inspection i "
            + "where d.status = 'open' and d.severity = org.fleetguard.entity.DefectSeverity.BLOCKING "
            + "and i.vehicleId in :vehicleIds order by d.createdAt")
    List<Defect> findOpenBlockingWithInspection(@Param("vehicleIds") Collection<UUID> vehicleIds);

    /** CAM-103: quiénes reportaron los bloqueantes de ese vehículo que se resolvieron desde {@code since}. */
    @Query("select distinct i.driverId from Defect d join d.inspectionAnswer a join a.inspection i "
            + "where i.vehicleId = :vehicleId and d.severity = org.fleetguard.entity.DefectSeverity.BLOCKING "
            + "and d.status = 'resuelto' and d.resolvedAt >= :since")
    List<String> findReportersOfBlockingResolvedSince(@Param("vehicleId") UUID vehicleId, @Param("since") Instant since);

    /** Defectos de esa gravedad reportados en [from, to), por chofer -- CAM-91. */
    @Query("select new org.fleetguard.repository.DriverCount(i.driverId, count(d)) from Defect d "
            + "join d.inspectionAnswer a join a.inspection i "
            + "where d.severity = :severity and d.createdAt >= :from and d.createdAt < :to group by i.driverId")
    List<DriverCount> countByDriver(@Param("severity") DefectSeverity severity,
                                    @Param("from") Instant from, @Param("to") Instant to);
}
