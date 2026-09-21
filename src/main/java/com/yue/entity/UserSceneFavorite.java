package com.yue.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "user_scene_favorites", uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "sceneId"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserSceneFavorite {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false) private Long sceneId;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @PrePersist void created() { if (createdAt == null) createdAt = Instant.now(); }
}
