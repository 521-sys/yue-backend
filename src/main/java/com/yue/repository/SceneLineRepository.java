package com.yue.repository;
import com.yue.entity.SceneLine;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface SceneLineRepository extends JpaRepository<SceneLine, Long> {
    List<SceneLine> findBySceneIdOrderBySequenceNoAsc(Long sceneId);
    void deleteBySceneId(Long sceneId);
}
