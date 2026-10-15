package com.yue.repository;
import com.yue.entity.SceneLine;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface SceneLineRepository extends JpaRepository<SceneLine, Long> {
    long countByAudioKey(String key);
    List<SceneLine> findByAudioKey(String key);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    @org.springframework.data.jpa.repository.Query("select l from SceneLine l where l.audioKey=:key")
    List<SceneLine> currentReferences(String key);
    List<SceneLine> findBySceneIdOrderBySequenceNoAsc(Long sceneId);
    void deleteBySceneId(Long sceneId);
}
