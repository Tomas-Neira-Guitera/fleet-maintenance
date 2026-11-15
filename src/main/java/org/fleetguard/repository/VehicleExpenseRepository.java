package org.fleetguard.repository;

import org.fleetguard.entity.VehicleExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface VehicleExpenseRepository extends JpaRepository<VehicleExpense, UUID> {

    /**
     * Gastos operativos de los días [from, to], los dos incluidos, con su vehículo -- CAM-109. Todos los
     * de un día salen a la misma hora: createdAt desempata, para que las filas no cambien de lugar.
     */
    @Query("select e from VehicleExpense e join fetch e.vehicle where e.date >= :from and e.date <= :to "
            + "order by e.date desc, e.createdAt desc")
    List<VehicleExpense> findInRangeWithVehicle(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Todos los gastos operativos de un vehículo -- CAM-109. */
    List<VehicleExpense> findByVehicle_Id(UUID vehicleId);
}
