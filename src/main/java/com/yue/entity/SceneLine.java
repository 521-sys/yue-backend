package com.yue.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "scene_lines", uniqueConstraints = @UniqueConstraint(columnNames = {"sceneId", "sequenceNo"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SceneLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long sceneId;
    @Column(nullable = false) private Integer sequenceNo;
    @Column(nullable = false, length = 1) private String speaker;
    @Column(nullable = false, columnDefinition = "TEXT") private String cantoneseText;
    @Column(nullable = false, columnDefinition = "TEXT") private String mandarinText;
    @Column(length = 80) private String audioKey;
    @Column(length = 2000) private String jyutping;
}
