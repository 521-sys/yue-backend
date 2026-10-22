package com.yue.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "dubbing_parts",
        uniqueConstraints = @UniqueConstraint(name = "uk_dubbing_part_material_code", columnNames = {"materialId", "code"}),
        indexes = @Index(name = "idx_dubbing_part_material_sort", columnList = "materialId,sortOrder,id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DubbingPart {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long materialId;
    @Column(nullable = false, length = 80) private String code;
    @Column(nullable = false, length = 40) private String label;
    @Column(nullable = false, length = 120) private String title;
    @Builder.Default @Column(nullable = false) private int sortOrder = 0;
    @Column(unique = true, length = 80) private String videoKey;
    @Column(length = 200) private String originalName;
    @Column(length = 80) private String fileName;
    @Column(length = 40) private String contentType;
    private Long sizeBytes;
    private Long durationMs;
    @Column(length = 20) private String videoCodec;
    @Column(length = 20) private String audioCodec;
    @Builder.Default @Column(nullable = false) private long videoVersion = 0L;
    @Version @Builder.Default @Column(nullable = false) private Long version = 0L;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;

    @PrePersist
    void created() {
        if (createdAt == null) createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void updated() {
        updatedAt = Instant.now();
    }
}
