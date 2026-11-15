package org.fleetguard.repository;

import org.fleetguard.entity.UnattendedDefectAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface UnattendedDefectAlertRepository extends JpaRepository<UnattendedDefectAlert, UUID> {

    /** CAM-142: de esos defectos, los que ya se avisaron. */
    @Query("select a.defectId from UnattendedDefectAlert a where a.defectId in :defectIds")
    List<UUID> findAlertedDefectIds(@Param("defectIds") Collection<UUID> defectIds);
}
