package com.yue.repository;

import com.yue.entity.DubbingMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface DubbingMaterialRepository extends JpaRepository<DubbingMaterial, Long>, JpaSpecificationExecutor<DubbingMaterial> {
    boolean existsByCode(String code);
    Optional<DubbingMaterial> findByCode(String code);
    List<DubbingMaterial> findByStatusOrderBySortOrderAscIdAsc(String status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select material from DubbingMaterial material where material.id = :id")
    Optional<DubbingMaterial> lockById(@Param("id") Long id);
}
