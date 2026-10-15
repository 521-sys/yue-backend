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
    @Builder.Default @Column(nullable=false, length=40) private String category = "日常生活";
    @Builder.Default @Column(nullable=false, length=10) private String difficulty = "初级";
    @Builder.Default @Column(nullable=false, length=12) private String status = "DRAFT";
    @Builder.Default @Column(nullable=false) private int sortOrder = 0;
    @Version @Builder.Default @Column(nullable=false) private Long version = 0L;
    @Column(nullable=false) private Instant updatedAt;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @PrePersist void created() { if (createdAt == null) createdAt = Instant.now(); updatedAt = Instant.now(); }
    @PreUpdate void updated() { updatedAt = Instant.now(); }
}
