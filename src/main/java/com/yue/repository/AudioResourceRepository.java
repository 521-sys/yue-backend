package com.yue.repository;
import com.yue.entity.AudioResource;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface AudioResourceRepository extends JpaRepository<AudioResource,Long>, JpaSpecificationExecutor<AudioResource> {
 Optional<AudioResource> findByAudioKey(String key);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from AudioResource a where a.audioKey=:key")
 Optional<AudioResource> lockByKey(String key);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from AudioResource a where a.id=:id")
 Optional<AudioResource> lockById(Long id);
 @Lock(LockModeType.PESSIMISTIC_READ) @Query("select a from AudioResource a where a.audioKey=:key")
 Optional<AudioResource> readLockedByKey(String key);
}
