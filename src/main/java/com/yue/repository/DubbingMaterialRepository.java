package com.yue.repository;

import com.yue.entity.DubbingMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface DubbingMaterialRepository extends JpaRepository<DubbingMaterial, Long>, JpaSpecificationExecutor<DubbingMaterial> {
    boolean existsByCode(String code);
    Optional<DubbingMaterial> findByCode(String code);
    List<DubbingMaterial> findByStatusOrderBySortOrderAscIdAsc(String status);
}
