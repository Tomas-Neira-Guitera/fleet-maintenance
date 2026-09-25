package org.example.repository;

import org.example.entity.Defect;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
