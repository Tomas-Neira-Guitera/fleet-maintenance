package org.fleetguard.repository;

import org.fleetguard.entity.TireConditionReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TireConditionReportRepository extends JpaRepository<TireConditionReport, UUID> {

    @Query("select r from TireConditionReport r join fetch r.tire join fetch r.vehicle left join fetch r.defect order by r.reportedAt desc")
    List<TireConditionReport> findAllWithTireAndDefect();

    @Query("select r from TireConditionReport r join fetch r.tire join fetch r.vehicle left join fetch r.defect where r.tire.id = :tireId "
            + "order by r.reportedAt desc")
    List<TireConditionReport> findByTireWithDefect(@Param("tireId") UUID tireId);
}
