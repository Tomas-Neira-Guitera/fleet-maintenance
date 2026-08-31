package org.example.repository;

import org.example.entity.Defect;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** No se usa en CAM-11 (no hay GET /api/defects) -- listo para la futura historia de defectos. */
public interface DefectRepository extends JpaRepository<Defect, UUID> {
}
