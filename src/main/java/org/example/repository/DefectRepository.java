package org.example.repository;

import org.example.entity.Defect;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Not consumed by CAM-11 (no GET /api/defects here) -- exists so the future defect-listing story has a repository ready. */
public interface DefectRepository extends JpaRepository<Defect, UUID> {
}
