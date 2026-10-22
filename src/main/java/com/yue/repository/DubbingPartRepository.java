package com.yue.repository;

import com.yue.entity.DubbingPart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DubbingPartRepository extends JpaRepository<DubbingPart, Long> {
    List<DubbingPart> findByMaterialIdOrderBySortOrderAscIdAsc(Long materialId);
    Optional<DubbingPart> findByMaterialIdAndCode(Long materialId, String code);
    boolean existsByMaterialIdAndCode(Long materialId, String code);
    void deleteByMaterialId(Long materialId);
}
