package org.example.repository;

import org.example.entity.Defect;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DefectRepository extends JpaRepository<Defect, UUID> {

    /** Trae inspectionAnswer + inspection en la misma consulta para no hacer N+1 al armar el listado. */
    @Query("select d from Defect d join fetch d.inspectionAnswer a join fetch a.inspection i")
    List<Defect> findAllWithInspection();

    /** Trae los defectos de un vehiculo puntual -- alimenta el historial de CAM-15 y el selector de CAM-14. */
    @Query("select d from Defect d join fetch d.inspectionAnswer a join fetch a.inspection i where i.vehicleId = :vehicleId")
    List<Defect> findAllByVehicleId(@Param("vehicleId") UUID vehicleId);
}
