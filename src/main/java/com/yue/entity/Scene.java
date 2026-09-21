package com.yue.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "scenes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Scene {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 80) private String code;
    @Column(nullable = false, length = 80) private String title;
    @Column(nullable = false, length = 80) private String place;
    @Column(length = 16) private String emoji;
    @Column(nullable = false, length = 40) private String roleA;
    @Column(nullable = false, length = 40) private String roleB;
    @Column private Long ownerUserId;
    @Column(nullable = false) private boolean builtin;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @PrePersist void created() { if (createdAt == null) createdAt = Instant.now(); }
}
