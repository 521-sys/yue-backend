package com.yue.repository;

import com.yue.entity.DubbingCue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DubbingCueRepository extends JpaRepository<DubbingCue, Long> {
    List<DubbingCue> findByPartIdOrderBySequenceNoAscIdAsc(Long partId);
    boolean existsByPartIdAndCode(Long partId, String code);
    void deleteByPartId(Long partId);
    void deleteByPartIdIn(List<Long> partIds);
}
