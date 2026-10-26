package com.yue.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "dubbing_materials", indexes = {
        @Index(name = "idx_dubbing_material_status_sort", columnList = "status,sortOrder,id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DubbingMaterial {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 80) private String code;
    @Column(nullable = false, length = 120) private String title;
    @Column(nullable = false, length = 20) private String difficulty;
    @Column(nullable = false, columnDefinition = "TEXT") private String tagsJson;
    @Column(nullable = false, length = 200) private String sourceLabel;
    @Column(nullable = false, length = 1000) private String sourceUrl;
    @Column(nullable = false, length = 80) private String licenseLabel;
    @Column(nullable = false, length = 1000) private String licenseUrl;
    @Column(length=80) private String coverKey;
    @Column(length=200) private String coverOriginalName;
    @Column(length=80) private String coverFileName;
    @Column(length=40) private String coverContentType;
    private Long coverSizeBytes;
    private Integer coverWidth;
    private Integer coverHeight;
    @Builder.Default @Column(nullable=false) private long coverVersion=0;
    @Builder.Default @Column(nullable = false, length = 12) private String status = "DRAFT";
    @Builder.Default @Column(nullable = false) private int sortOrder = 0;
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
