package com.yue.repository;
import com.yue.entity.UserSceneFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface UserSceneFavoriteRepository extends JpaRepository<UserSceneFavorite, Long> {
    void deleteBySceneId(Long sceneId);
    boolean existsByUserIdAndSceneId(Long userId, Long sceneId);
    void deleteByUserIdAndSceneId(Long userId, Long sceneId);
    List<UserSceneFavorite> findByUserIdOrderByCreatedAtDesc(Long userId);
}
