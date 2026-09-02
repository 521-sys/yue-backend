package com.yue.repository;

import com.yue.entity.LearnState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 学习状态仓储：按 userId 查询，每用户一行。
 */
public interface LearnStateRepository extends JpaRepository<LearnState, Long> {

    Optional<LearnState> findByUserId(Long userId);
}
