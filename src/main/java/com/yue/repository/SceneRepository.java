package com.yue.repository;
import com.yue.entity.Scene;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface SceneRepository extends JpaRepository<Scene, Long> {
    List<Scene> findAllByOrderByCreatedAtDesc();
    List<Scene> findByOwnerUserIdOrderByCreatedAtDesc(Long ownerUserId);
}
